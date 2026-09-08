#ifdef BURN_HAVE_FFMPEG

#include "ffmpeg_burn.h"
#include "log_bridge.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavcodec/bsf.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersink.h>
#include <libavfilter/buffersrc.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavutil/display.h>
#include <libavutil/opt.h>
#include <libavutil/pixfmt.h>
#include <libavutil/time.h>
}

#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

namespace {

constexpr int kCancelled = 255;
constexpr int kError = 1;

struct Pipeline {
    AVFormatContext *in{};
    AVFormatContext *out{};
    AVCodecContext *decoder{};
    AVCodecContext *encoder{};
    AVFilterGraph *graph{};
    AVFilterContext *src{};
    AVFilterContext *sink{};
    AVPacket *ipkt{};
    AVPacket *opkt{};
    AVFrame *frame{};
    AVFrame *filtered{};
    AVBSFContext *audio_bsf{};
    int video_in = -1;
    int audio_in = -1;
    int video_out = -1;
    int audio_out = -1;
};

int interrupt_cb(void * /* opaque */) {
    return burn_is_cancelled();
}

void log_cb(void *ptr, int level, const char *fmt, va_list vl) {
    if (level > av_log_get_level()) {
        return;
    }
    char line[1024];
    int print_prefix = 1;
    av_log_format_line(ptr, level, fmt, vl, line, sizeof(line), &print_prefix);
    size_t n = strlen(line);
    while (n > 0 && (line[n - 1] == '\n' || line[n - 1] == '\r')) {
        line[--n] = 0;
    }
    if (n > 0) {
        burn_forward_log(line);
    }
}

// Mirrors fftools' autorotate handling: phone recordings are stored landscape with a
// display matrix, so the frame has to be rotated before subtitles are burned in.
// Otherwise the export is sideways and the text is rotated with it.
std::string autorotate_prefix(AVStream *st, bool *swaps_dimensions) {
    *swaps_dimensions = false;
    const AVPacketSideData *sd = av_packet_side_data_get(
            st->codecpar->coded_side_data,
            st->codecpar->nb_coded_side_data,
            AV_PKT_DATA_DISPLAYMATRIX);
    if (sd == nullptr || sd->size < 9 * sizeof(int32_t)) {
        return "";
    }
    const int32_t *matrix = reinterpret_cast<const int32_t *>(sd->data);
    double theta = -std::round(av_display_rotation_get(matrix));
    theta -= 360 * std::floor(theta / 360 + 0.9 / 360);
    if (std::fabs(theta - 90) < 1.0) {
        *swaps_dimensions = true;
        return matrix[3] > 0 ? "transpose=cclock_flip," : "transpose=clock,";
    }
    if (std::fabs(theta - 180) < 1.0) {
        std::string filters;
        if (matrix[0] < 0) filters += "hflip,";
        if (matrix[4] < 0) filters += "vflip,";
        return filters;
    }
    if (std::fabs(theta - 270) < 1.0) {
        *swaps_dimensions = true;
        return matrix[3] < 0 ? "transpose=clock_flip," : "transpose=cclock,";
    }
    return "";
}

std::string escape_filter_path(const char *path) {
    std::string out;
    out.reserve(strlen(path) * 2 + 8);
    for (const char *p = path; *p; ++p) {
        if (*p == '\\') {
            out += '/';
        } else {
            if (*p == ':' || *p == '\'' || *p == '[' || *p == ']' || *p == ',' || *p == ';' || *p == '=' || *p == ' ') {
                out += '\\';
            }
            out += *p;
        }
    }
    return out;
}

void close_pipeline(Pipeline *p) {
    av_bsf_free(&p->audio_bsf);
    avfilter_graph_free(&p->graph);
    avcodec_free_context(&p->decoder);
    avcodec_free_context(&p->encoder);
    if (p->out) {
        if (!(p->out->oformat->flags & AVFMT_NOFILE) && p->out->pb) {
            avio_closep(&p->out->pb);
        }
        avformat_free_context(p->out);
        p->out = nullptr;
    }
    if (p->in) {
        avformat_close_input(&p->in);
    }
    av_packet_free(&p->ipkt);
    av_packet_free(&p->opkt);
    av_frame_free(&p->frame);
    av_frame_free(&p->filtered);
}

int open_input(Pipeline *p, const char *path) {
    p->in = nullptr;
    AVDictionary *opts = nullptr;
    av_dict_set(&opts, "scan_all_pmts", "1", 0);
    int err = avformat_open_input(&p->in, path, nullptr, &opts);
    av_dict_free(&opts);
    if (err < 0) {
        return err;
    }
    p->in->interrupt_callback.callback = interrupt_cb;
    err = avformat_find_stream_info(p->in, nullptr);
    if (err < 0) {
        return err;
    }
    p->video_in = av_find_best_stream(p->in, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    p->audio_in = av_find_best_stream(p->in, AVMEDIA_TYPE_AUDIO, -1, p->video_in, nullptr, 0);
    if (p->video_in < 0) {
        return AVERROR_STREAM_NOT_FOUND;
    }
    return 0;
}

int open_decoder(Pipeline *p) {
    AVStream *st = p->in->streams[p->video_in];
    const AVCodec *codec = avcodec_find_decoder(st->codecpar->codec_id);
    if (!codec) {
        return AVERROR_DECODER_NOT_FOUND;
    }
    p->decoder = avcodec_alloc_context3(codec);
    if (!p->decoder) {
        return AVERROR(ENOMEM);
    }
    int err = avcodec_parameters_to_context(p->decoder, st->codecpar);
    if (err < 0) {
        return err;
    }
    p->decoder->pkt_timebase = st->time_base;
    err = avcodec_open2(p->decoder, codec, nullptr);
    return err;
}

int build_filters(Pipeline *p, const char *ass, const char *fonts) {
    p->graph = avfilter_graph_alloc();
    if (!p->graph) {
        return AVERROR(ENOMEM);
    }
    const AVFilter *buffersrc = avfilter_get_by_name("buffer");
    const AVFilter *buffersink = avfilter_get_by_name("buffersink");
    if (!buffersrc || !buffersink) {
        return AVERROR_FILTER_NOT_FOUND;
    }
    char args[320];
    // Decoded frames carry PTS in the input stream's time base, not in the decoder's
    // (unset) time_base. Feeding buffersrc the wrong unit shifts every subtitle.
    AVStream *in_stream = p->in->streams[p->video_in];
    AVRational tb = in_stream->time_base;
    if (tb.num <= 0 || tb.den <= 0) {
        tb = p->decoder->pkt_timebase.num > 0 ? p->decoder->pkt_timebase : av_make_q(1, AV_TIME_BASE);
    }
    AVRational sar = p->decoder->sample_aspect_ratio.num ? p->decoder->sample_aspect_ratio : av_make_q(1, 1);
    AVRational fps = av_guess_frame_rate(p->in, in_stream, nullptr);
    if (fps.num <= 0 || fps.den <= 0) {
        fps = av_make_q(0, 1);
    }
    snprintf(
            args,
            sizeof(args),
            "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d:frame_rate=%d/%d",
            p->decoder->width,
            p->decoder->height,
            p->decoder->pix_fmt,
            tb.num,
            tb.den,
            sar.num,
            sar.den,
            fps.num,
            fps.den);
    int err = avfilter_graph_create_filter(&p->src, buffersrc, "in", args, nullptr, p->graph);
    if (err < 0) {
        return err;
    }
    err = avfilter_graph_create_filter(&p->sink, buffersink, "out", nullptr, nullptr, p->graph);
    if (err < 0) {
        return err;
    }
    enum AVPixelFormat pix[] = {AV_PIX_FMT_YUV420P, AV_PIX_FMT_NONE};
    err = av_opt_set_int_list(p->sink, "pix_fmts", pix, AV_PIX_FMT_NONE, AV_OPT_SEARCH_CHILDREN);
    if (err < 0) {
        return err;
    }
    bool rotation_swaps_dimensions = false;
    std::string filt = autorotate_prefix(in_stream, &rotation_swaps_dimensions);
    // original_size must describe the frame the subtitles are drawn onto, which is the
    // upright frame produced by the rotation filters above.
    const int upright_w = rotation_swaps_dimensions ? p->decoder->height : p->decoder->width;
    const int upright_h = rotation_swaps_dimensions ? p->decoder->width : p->decoder->height;
    filt += "subtitles=filename=";
    filt += escape_filter_path(ass);
    filt += ":fontsdir=";
    filt += escape_filter_path(fonts);
    filt += ":original_size=";
    filt += std::to_string(upright_w);
    filt += "x";
    filt += std::to_string(upright_h);
    filt += ",format=yuv420p";
    burn_forward_log(("filter: " + filt).c_str());
    AVFilterInOut *outputs = avfilter_inout_alloc();
    AVFilterInOut *inputs = avfilter_inout_alloc();
    if (!outputs || !inputs) {
        avfilter_inout_free(&outputs);
        avfilter_inout_free(&inputs);
        return AVERROR(ENOMEM);
    }
    outputs->name = av_strdup("in");
    outputs->filter_ctx = p->src;
    outputs->pad_idx = 0;
    outputs->next = nullptr;
    inputs->name = av_strdup("out");
    inputs->filter_ctx = p->sink;
    inputs->pad_idx = 0;
    inputs->next = nullptr;
    err = avfilter_graph_parse_ptr(p->graph, filt.c_str(), &inputs, &outputs, nullptr);
    avfilter_inout_free(&outputs);
    avfilter_inout_free(&inputs);
    if (err < 0) {
        return err;
    }
    return avfilter_graph_config(p->graph, nullptr);
}

int alloc_output(Pipeline *p, const char *path) {
    return avformat_alloc_output_context2(&p->out, nullptr, "mp4", path);
}

int open_encoder(Pipeline *p, int crf, const char *preset) {
    // Only libx264 is supported. A generic H.264 lookup can land on h264_mediacodec,
    // which needs hardware frame contexts this synchronous pipeline never sets up.
    const AVCodec *codec = avcodec_find_encoder_by_name("libx264");
    if (!codec) {
        return AVERROR_ENCODER_NOT_FOUND;
    }
    p->encoder = avcodec_alloc_context3(codec);
    if (!p->encoder) {
        return AVERROR(ENOMEM);
    }
    p->encoder->width = av_buffersink_get_w(p->sink);
    p->encoder->height = av_buffersink_get_h(p->sink);
    p->encoder->pix_fmt = AV_PIX_FMT_YUV420P;
    p->encoder->time_base = av_buffersink_get_time_base(p->sink);
    if (p->encoder->time_base.num <= 0 || p->encoder->time_base.den <= 0) {
        p->encoder->time_base = av_make_q(1, 90000);
    }
    p->encoder->framerate = av_buffersink_get_frame_rate(p->sink);
    p->encoder->sample_aspect_ratio = av_buffersink_get_sample_aspect_ratio(p->sink);
    p->encoder->gop_size = 48;
    p->encoder->max_b_frames = 2;
    if (p->out && p->out->oformat && (p->out->oformat->flags & AVFMT_GLOBALHEADER)) {
        p->encoder->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }
    AVDictionary *opts = nullptr;
    char crf_value[8];
    snprintf(crf_value, sizeof(crf_value), "%d", crf);
    av_dict_set(&opts, "crf", crf_value, 0);
    av_dict_set(&opts, "preset", preset && preset[0] ? preset : "veryfast", 0);
    int err = avcodec_open2(p->encoder, codec, &opts);
    av_dict_free(&opts);
    return err;
}

/**
 * MPEG-TS and some MKV files carry AAC with ADTS headers, which the MP4 muxer rejects.
 * Running those packets through aac_adtstoasc converts them to the raw form MP4 expects
 * and produces the codec extradata the output stream needs.
 */
int init_audio_bsf(Pipeline *p, const AVStream *in_audio, AVStream *out_audio) {
    if (in_audio->codecpar->codec_id != AV_CODEC_ID_AAC) {
        return 0;
    }
    const AVBitStreamFilter *filter = av_bsf_get_by_name("aac_adtstoasc");
    if (!filter) {
        return 0;
    }
    int err = av_bsf_alloc(filter, &p->audio_bsf);
    if (err < 0) {
        return err;
    }
    err = avcodec_parameters_copy(p->audio_bsf->par_in, in_audio->codecpar);
    if (err < 0) {
        return err;
    }
    p->audio_bsf->time_base_in = in_audio->time_base;
    err = av_bsf_init(p->audio_bsf);
    if (err < 0) {
        return err;
    }
    err = avcodec_parameters_copy(out_audio->codecpar, p->audio_bsf->par_out);
    if (err < 0) {
        return err;
    }
    out_audio->codecpar->codec_tag = 0;
    return 0;
}

int write_audio_packet(Pipeline *p, AVPacket *pkt) {
    AVStream *out_audio = p->out->streams[p->audio_out];
    if (!p->audio_bsf) {
        av_packet_rescale_ts(pkt, p->in->streams[p->audio_in]->time_base, out_audio->time_base);
        pkt->stream_index = p->audio_out;
        return av_interleaved_write_frame(p->out, pkt);
    }
    int err = av_bsf_send_packet(p->audio_bsf, pkt);
    if (err < 0) {
        return err;
    }
    while (true) {
        err = av_bsf_receive_packet(p->audio_bsf, pkt);
        if (err == AVERROR(EAGAIN) || err == AVERROR_EOF) {
            return 0;
        }
        if (err < 0) {
            return err;
        }
        av_packet_rescale_ts(pkt, p->audio_bsf->time_base_out, out_audio->time_base);
        pkt->stream_index = p->audio_out;
        err = av_interleaved_write_frame(p->out, pkt);
        av_packet_unref(pkt);
        if (err < 0) {
            return err;
        }
    }
}

int write_output_header(Pipeline *p, const char *path) {
    AVStream *vs = avformat_new_stream(p->out, nullptr);
    if (!vs) {
        return AVERROR(ENOMEM);
    }
    p->video_out = vs->index;
    int err = avcodec_parameters_from_context(vs->codecpar, p->encoder);
    if (err < 0) {
        return err;
    }
    vs->time_base = p->encoder->time_base;
    if (p->audio_in >= 0) {
        AVStream *in_audio = p->in->streams[p->audio_in];
        AVStream *as = avformat_new_stream(p->out, nullptr);
        if (!as) {
            return AVERROR(ENOMEM);
        }
        p->audio_out = as->index;
        err = avcodec_parameters_copy(as->codecpar, in_audio->codecpar);
        if (err < 0) {
            return err;
        }
        as->codecpar->codec_tag = 0;
        as->time_base = in_audio->time_base;
        err = init_audio_bsf(p, in_audio, as);
        if (err < 0) {
            return err;
        }
    }
    if (!(p->out->oformat->flags & AVFMT_NOFILE)) {
        err = avio_open(&p->out->pb, path, AVIO_FLAG_WRITE);
        if (err < 0) {
            return err;
        }
    }
    AVDictionary *opts = nullptr;
    av_dict_set(&opts, "movflags", "+faststart", 0);
    err = avformat_write_header(p->out, &opts);
    av_dict_free(&opts);
    return err;
}

int encode_frame(Pipeline *p, AVFrame *frame) {
    int err = avcodec_send_frame(p->encoder, frame);
    if (err < 0) {
        return err;
    }
    while (err >= 0) {
        err = avcodec_receive_packet(p->encoder, p->opkt);
        if (err == AVERROR(EAGAIN) || err == AVERROR_EOF) {
            return 0;
        }
        if (err < 0) {
            return err;
        }
        av_packet_rescale_ts(p->opkt, p->encoder->time_base, p->out->streams[p->video_out]->time_base);
        p->opkt->stream_index = p->video_out;
        err = av_interleaved_write_frame(p->out, p->opkt);
        av_packet_unref(p->opkt);
        if (err < 0) {
            return err;
        }
    }
    return 0;
}

int filter_and_encode(Pipeline *p, AVFrame *in, long duration_ms) {
    int err = av_buffersrc_add_frame_flags(p->src, in, AV_BUFFERSRC_FLAG_KEEP_REF);
    if (err < 0) {
        return err;
    }
    while (true) {
        if (burn_is_cancelled()) {
            return AVERROR_EXIT;
        }
        err = av_buffersink_get_frame(p->sink, p->filtered);
        if (err == AVERROR(EAGAIN) || err == AVERROR_EOF) {
            return 0;
        }
        if (err < 0) {
            return err;
        }
        if (p->filtered->pts != AV_NOPTS_VALUE) {
            const AVRational tb = av_buffersink_get_time_base(p->sink);
            const long time_ms = static_cast<long>(p->filtered->pts * av_q2d(tb) * 1000.0);
            burn_forward_progress(time_ms);
            (void) duration_ms;
        }
        err = encode_frame(p, p->filtered);
        av_frame_unref(p->filtered);
        if (err < 0) {
            return err;
        }
    }
}

int process(Pipeline *p, long duration_ms) {
    int err;
    while ((err = av_read_frame(p->in, p->ipkt)) >= 0) {
        if (burn_is_cancelled()) {
            av_packet_unref(p->ipkt);
            return AVERROR_EXIT;
        }
        if (p->ipkt->stream_index == p->video_in) {
            err = avcodec_send_packet(p->decoder, p->ipkt);
            av_packet_unref(p->ipkt);
            if (err < 0) {
                return err;
            }
            while (true) {
                err = avcodec_receive_frame(p->decoder, p->frame);
                if (err == AVERROR(EAGAIN) || err == AVERROR_EOF) {
                    break;
                }
                if (err < 0) {
                    return err;
                }
                err = filter_and_encode(p, p->frame, duration_ms);
                av_frame_unref(p->frame);
                if (err < 0) {
                    return err;
                }
            }
        } else if (p->audio_out >= 0 && p->ipkt->stream_index == p->audio_in) {
            err = write_audio_packet(p, p->ipkt);
            av_packet_unref(p->ipkt);
            if (err < 0) {
                return err;
            }
        } else {
            av_packet_unref(p->ipkt);
        }
    }
    if (err != AVERROR_EOF && err < 0) {
        return err;
    }
    avcodec_send_packet(p->decoder, nullptr);
    while (avcodec_receive_frame(p->decoder, p->frame) >= 0) {
        err = filter_and_encode(p, p->frame, duration_ms);
        av_frame_unref(p->frame);
        if (err < 0) {
            return err;
        }
    }
    err = av_buffersrc_add_frame_flags(p->src, nullptr, 0);
    if (err < 0) {
        return err;
    }
    while (av_buffersink_get_frame(p->sink, p->filtered) >= 0) {
        err = encode_frame(p, p->filtered);
        av_frame_unref(p->filtered);
        if (err < 0) {
            return err;
        }
    }
    err = encode_frame(p, nullptr);
    if (err < 0) {
        return err;
    }
    return av_write_trailer(p->out);
}

}  // namespace

int burn_subtitles(
        const char *input_path,
        const char *output_path,
        const char *ass_path,
        const char *fonts_dir,
        const char *fontconfig_path,
        int crf,
        const char *preset,
        long duration_ms) {
    av_log_set_callback(log_cb);
    av_log_set_level(AV_LOG_INFO);
    if (fontconfig_path && fontconfig_path[0]) {
        setenv("FONTCONFIG_FILE", fontconfig_path, 1);
    }
    burn_forward_log("Starting subtitle burn");
    Pipeline p{};
    p.ipkt = av_packet_alloc();
    p.opkt = av_packet_alloc();
    p.frame = av_frame_alloc();
    p.filtered = av_frame_alloc();
    int err = 0;
    if (!p.ipkt || !p.opkt || !p.frame || !p.filtered) {
        err = AVERROR(ENOMEM);
        goto done;
    }
    err = open_input(&p, input_path);
    if (err < 0) goto done;
    err = open_decoder(&p);
    if (err < 0) goto done;
    err = build_filters(&p, ass_path, fonts_dir);
    if (err < 0) goto done;
    err = alloc_output(&p, output_path);
    if (err < 0) goto done;
    p.out->interrupt_callback.callback = interrupt_cb;
    err = open_encoder(&p, crf, preset);
    if (err < 0) goto done;
    err = write_output_header(&p, output_path);
    if (err < 0) goto done;
    err = process(&p, duration_ms);

done:
    close_pipeline(&p);
    if (burn_is_cancelled()) {
        burn_forward_log("Burn cancelled");
        return kCancelled;
    }
    if (err < 0) {
        char buf[128];
        av_strerror(err, buf, sizeof(buf));
        burn_forward_log(buf);
        return kError;
    }
    burn_forward_log("Burn finished");
    burn_forward_progress(duration_ms > 0 ? duration_ms : 0);
    return 0;
}

#endif
