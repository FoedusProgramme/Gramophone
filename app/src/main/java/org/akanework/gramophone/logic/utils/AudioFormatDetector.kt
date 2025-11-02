package org.akanework.gramophone.logic.utils

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.os.Build
import android.os.Parcelable
import androidx.media3.common.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import kotlinx.parcelize.Parcelize
import org.akanework.gramophone.R

object AudioFormatDetector {
    fun channelConfigToString(context: Context, format: Int?): String {
        return when (format) {
            AudioFormat.CHANNEL_OUT_MONO -> context.getString(R.string.spk_channel_out_mono)
            AudioFormat.CHANNEL_OUT_STEREO -> context.getString(R.string.spk_channel_out_stereo)
            AudioFormat.CHANNEL_OUT_QUAD -> context.getString(R.string.spk_channel_out_quad)
            AudioFormat.CHANNEL_OUT_SURROUND -> context.getString(R.string.spk_channel_out_surround)
            AudioFormat.CHANNEL_OUT_5POINT1 -> context.getString(R.string.spk_channel_out_5point1)
            AudioFormat.CHANNEL_OUT_6POINT1 -> context.getString(R.string.spk_channel_out_6point1)
            AudioFormat.CHANNEL_OUT_7POINT1_SURROUND -> context.getString(R.string.spk_channel_out_7point1_surround)
            AudioFormat.CHANNEL_OUT_5POINT1POINT2 -> context.getString(R.string.spk_channel_out_5point1point2)
            AudioFormat.CHANNEL_OUT_5POINT1POINT4 -> context.getString(R.string.spk_channel_out_5point1point4)
            AudioFormat.CHANNEL_OUT_7POINT1POINT2 -> context.getString(R.string.spk_channel_out_7point1point2)
            AudioFormat.CHANNEL_OUT_7POINT1POINT4 -> context.getString(R.string.spk_channel_out_7point1point4)
            AudioFormat.CHANNEL_OUT_9POINT1POINT4 -> context.getString(R.string.spk_channel_out_9point1point4)
            AudioFormat.CHANNEL_OUT_9POINT1POINT6 -> context.getString(R.string.spk_channel_out_9point1point6)
            AudioFormat.CHANNEL_INVALID, null -> context.getString(R.string.spk_channel_invalid)
            else -> {
                val str = mutableListOf<String>()
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_LOW_FREQUENCY) != 0) {
                    str += context.getString(R.string.spk_channel_out_low_frequency)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BACK_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_back_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BACK_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_back_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_LEFT_OF_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_left_of_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_RIGHT_OF_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_right_of_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BACK_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_back_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_SIDE_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_side_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_SIDE_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_side_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_FRONT_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_front_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_FRONT_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_front_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_FRONT_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_front_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_BACK_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_back_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_BACK_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_back_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_BACK_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_back_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_SIDE_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_side_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_SIDE_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_side_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BOTTOM_FRONT_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_bottom_front_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BOTTOM_FRONT_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_bottom_front_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BOTTOM_FRONT_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_bottom_front_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_LOW_FREQUENCY_2) != 0) {
                    str += context.getString(R.string.spk_channel_out_low_frequency_2)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_WIDE_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_wide_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_WIDE_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_wide_right)
                }
                if (str.isEmpty())
                    context.getString(R.string.spk_channel_invalid)
                str.joinToString(" | ")
            }
        }
    }

    enum class ChannelMask {
        // TODO
    }

    enum class Encoding(val enc: Int?, val enc2: String?, val native: UInt?, val sdkRange: IntRange?, val res: Int) {
        ENCODING_INVALID(C.ENCODING_INVALID, null, 0xFFFFFFFFU, 0, R.string.spk_encoding_invalid),
        ENCODING_PCM_8BIT(C.ENCODING_PCM_8BIT, "AUDIO_FORMAT_PCM_8_BIT", 0x2U, 21, R.string.spk_encoding_pcm_8bit),
        ENCODING_PCM_16BIT(C.ENCODING_PCM_16BIT, "AUDIO_FORMAT_PCM_16_BIT", 0x1U, 21, R.string.spk_encoding_pcm_16bit),
        ENCODING_PCM_16BIT_BIG_ENDIAN(C.ENCODING_PCM_16BIT_BIG_ENDIAN, null, null, null, R.string.spk_encoding_pcm_16bit_big_endian),
        ENCODING_PCM_20BIT(C.ENCODING_PCM_20BIT, null, null, null, R.string.spk_encoding_pcm_20bit),
        ENCODING_PCM_20BIT_BIG_ENDIAN(C.ENCODING_PCM_20BIT_BIG_ENDIAN, null, null, null, R.string.spk_encoding_pcm_20bit_big_endian),
        ENCODING_PCM_24BIT(C.ENCODING_PCM_24BIT, "AUDIO_FORMAT_PCM_24_BIT_PACKED", 0x6U, 21, R.string.spk_encoding_pcm_24bit),
        ENCODING_PCM_8_24BIT(null, "AUDIO_FORMAT_PCM_8_24_BIT", 0x4U, 21, R.string.spk_encoding_pcm_8_24bit),
        ENCODING_PCM_24BIT_BIG_ENDIAN(C.ENCODING_PCM_24BIT_BIG_ENDIAN, null, null, null, R.string.spk_encoding_pcm_24bit_big_endian),
        ENCODING_PCM_32BIT(C.ENCODING_PCM_32BIT, "AUDIO_FORMAT_PCM_32_BIT", 0x3U, 21, R.string.spk_encoding_pcm_32bit),
        ENCODING_PCM_32BIT_BIG_ENDIAN(C.ENCODING_PCM_32BIT_BIG_ENDIAN, null, null, null, R.string.spk_encoding_pcm_32bit_big_endian),
        ENCODING_PCM_FLOAT(C.ENCODING_PCM_FLOAT, "AUDIO_FORMAT_PCM_FLOAT", 0x5U, 21, R.string.spk_encoding_pcm_float),
        ENCODING_MP3(C.ENCODING_MP3, "AUDIO_FORMAT_MP3", 0x01000000U, 21, R.string.spk_encoding_mp3),
        ENCODING_AAC_LC(C.ENCODING_AAC_LC, "AUDIO_FORMAT_AAC_LC", 0x4000002U, 21, R.string.spk_encoding_aac_lc),
        ENCODING_AAC_HE_V1(C.ENCODING_AAC_HE_V1, "AUDIO_FORMAT_AAC_HE_V1", 0x4000010U, 21, R.string.spk_encoding_aac_he_v1),
        ENCODING_AAC_HE_V2(C.ENCODING_AAC_HE_V2, "AUDIO_FORMAT_AAC_HE_V2", 0x4000100U, 21, R.string.spk_encoding_aac_he_v2),
        ENCODING_HE_AAC_V1(C.ENCODING_AAC_HE_V1, "AUDIO_FORMAT_HE_AAC_V1", 0x05000000U, 21, R.string.spk_encoding_aac_he_v1), // deprecated
        ENCODING_HE_AAC_V2(C.ENCODING_AAC_HE_V2, "AUDIO_FORMAT_HE_AAC_V2", 0x06000000U, 21, R.string.spk_encoding_aac_he_v2), // deprecated
        ENCODING_AAC_XHE(C.ENCODING_AAC_XHE, "AUDIO_FORMAT_AAC_XHE", 0x4000300U, 28, R.string.spk_encoding_aac_xhe),
        ENCODING_AAC_ELD(C.ENCODING_AAC_ELD, "AUDIO_FORMAT_AAC_ELD", 0x4000200U, 21, R.string.spk_encoding_aac_eld),
        ENCODING_AAC_ER_BSAC(C.ENCODING_AAC_ER_BSAC, null, null, null, R.string.spk_encoding_aac_er_bsac),
        ENCODING_AC3(C.ENCODING_AC3, "AUDIO_FORMAT_AC3", 0x09000000U, 21, R.string.spk_encoding_ac3),
        ENCODING_E_AC3(C.ENCODING_E_AC3, "AUDIO_FORMAT_E_AC3", 0x0A000000U, 21, R.string.spk_encoding_e_ac3),
        ENCODING_E_AC3_JOC(C.ENCODING_E_AC3_JOC, "AUDIO_FORMAT_E_AC3_JOC", if (Build.VERSION.SDK_INT >= 28)
            0xA000001U else 0x1E000000U, if (Build.VERSION.SDK_INT >= 23) 28 else 21, R.string.spk_encoding_e_ac3_joc), // aosp since 28
        ENCODING_AC4(C.ENCODING_AC4, "AUDIO_FORMAT_AC4", 0x22000000U, 26, R.string.spk_encoding_ac4),
        ENCODING_DTS(C.ENCODING_DTS, "AUDIO_FORMAT_DTS", if (Build.VERSION.SDK_INT >= 23) 0x0B000000U else 0x12000000U, 21, R.string.spk_encoding_dts), // aosp since 23
        ENCODING_DTS_HD(C.ENCODING_DTS_HD, "AUDIO_FORMAT_DTS_HD", if (Build.VERSION.SDK_INT >= 23) 0x0C000000U else 0x18000000U, 21, R.string.spk_encoding_dts_hd), // aosp since 23
        ENCODING_DOLBY_TRUEHD(C.ENCODING_DOLBY_TRUEHD, "AUDIO_FORMAT_DOLBY_TRUEHD", 0x0E000000U, 25, R.string.spk_encoding_dolby_truehd),
        ENCODING_OPUS(C.ENCODING_OPUS, "AUDIO_FORMAT_OPUS", 0x08000000U, 21, R.string.spk_encoding_opus),
        ENCODING_DTS_UHD_P2(C.ENCODING_DTS_UHD_P2, "AUDIO_FORMAT_DTS_UHD_P2", 0x33000000U, 34, R.string.spk_encoding_dts_uhd_p2),
        ENCODING_AMR_NB(null, "AUDIO_FORMAT_AMR_NB", 0x02000000U, 21, R.string.spk_encoding_amr_nb),
        ENCODING_AMR_WB(null, "AUDIO_FORMAT_AMR_WB", 0x03000000U, 21, R.string.spk_encoding_amr_wb),
        ENCODING_AAC(null, "AUDIO_FORMAT_AAC", 0x04000000U, 21, R.string.spk_encoding_aac),
        ENCODING_VORBIS(null, "AUDIO_FORMAT_VORBIS", 0x07000000U, 21, R.string.spk_encoding_vorbis),
        ENCODING_IEC61937(null, "AUDIO_FORMAT_IEC61937", 0x0D000000U, 24, R.string.spk_encoding_iec61937),
        ENCODING_EVRC(null, "AUDIO_FORMAT_EVRC", 0x10000000U, 21, R.string.spk_encoding_evrc), // aosp since 26
        ENCODING_EVRCB(null, "AUDIO_FORMAT_EVRCB", if (Build.VERSION.SDK_INT >= 26) 0x11000000U else
            if (Build.VERSION.SDK_INT >= 23) 0x15000000U else 0x16000000U, 21, R.string.spk_encoding_evrcb), // aosp since 26
        ENCODING_EVRCWB(null, "AUDIO_FORMAT_EVRCWB", if (Build.VERSION.SDK_INT >= 26) 0x12000000U else
            if (Build.VERSION.SDK_INT >= 23) 0x16000000U else 0x17000000U, 21, R.string.spk_encoding_evrcwb), // aosp since 26
        ENCODING_EVRCNW(null, "AUDIO_FORMAT_EVRCNW", if (Build.VERSION.SDK_INT >= 26) 0x13000000U else
            if (Build.VERSION.SDK_INT >= 23) 0x19000000U else 0x1B000000U, 21, R.string.spk_encoding_evrcnw), // aosp since 26
        ENCODING_AAC_ADIF(null, "AUDIO_FORMAT_AAC_ADIF", if (Build.VERSION.SDK_INT >= 23) 0x14000000U else
            0x15000000U, 21, R.string.spk_encoding_aac_adif), // aosp since 26
        ENCODING_WMA(null, "AUDIO_FORMAT_WMA", if (Build.VERSION.SDK_INT >= 26) 0x15000000U else
            if (Build.VERSION.SDK_INT >= 23) 0x12000000U else 0x13000000U, 21, R.string.spk_encoding_wma),
        ENCODING_WMA_PRO(null, "AUDIO_FORMAT_WMA_PRO", if (Build.VERSION.SDK_INT >= 26) 0x16000000U else
            if (Build.VERSION.SDK_INT >= 23) 0x13000000U else 0x14000000U, 21, R.string.spk_encoding_wma_pro),
        ENCODING_AMR_WB_PLUS(null, "AUDIO_FORMAT_AMR_WB_PLUS", if (Build.VERSION.SDK_INT >= 23) 0x17000000U else
            0x19000000U, 21, R.string.spk_encoding_amr_wb_plus), // aosp since 26
        ENCODING_MP2(null, "AUDIO_FORMAT_MP2", if (Build.VERSION.SDK_INT >= 23) 0x18000000U else
            0x1A000000U, 21, R.string.spk_encoding_mp2), // aosp since 26
        ENCODING_QCELP(null, "AUDIO_FORMAT_QCELP", if (Build.VERSION.SDK_INT >= 26) 0x19000000U else
            0x11000000U, 26, R.string.spk_encoding_qcelp),
        ENCODING_PCM_16_BIT_OFFLOAD(null, "AUDIO_FORMAT_PCM_16_BIT_OFFLOAD", if (Build.VERSION.SDK_INT >= 23)
            0x1A000001U else 0x1C000001U, 21..25, R.string.spk_encoding_pcm_16bit_offload), // caf
        ENCODING_PCM_8_24_BIT_OFFLOAD(null, "AUDIO_FORMAT_PCM_24_BIT_OFFLOAD", if (Build.VERSION.SDK_INT >= 23)
            0x1A000004U else 0x1C000004U, 21..25, R.string.spk_encoding_pcm_24bit_offload), // caf
        ENCODING_DSD(null, "AUDIO_FORMAT_DSD", if (Build.VERSION.SDK_INT >= 26) 0x1A000000U else 0x1F000000U, 24, R.string.spk_encoding_dsd), // aosp since 26
        ENCODING_FLAC(null, "AUDIO_FORMAT_FLAC", if (Build.VERSION.SDK_INT >= 23) 0x1B000000U else 0x1D000000U, 21, R.string.spk_encoding_flac), // aosp since 26
        ENCODING_ALAC(null, "AUDIO_FORMAT_ALAC", 0x1C000000U, 23, R.string.spk_encoding_alac), // aosp since 26
        ENCODING_APE(null, "AUDIO_FORMAT_APE", 0x1D000000U, 23, R.string.spk_encoding_ape), // aosp since 26
        ENCODING_AAC_ADTS(null, "AUDIO_FORMAT_AAC_ADTS", 0x1E000000U, 23, R.string.spk_encoding_aac_adts), // aosp since 26
        ENCODING_SBC(null, "AUDIO_FORMAT_SBC", if (Build.VERSION.SDK_INT >= 26) 0x1F000000U else 0x20000000U, 24, R.string.spk_encoding_sbc), // aosp since 26
        ENCODING_APTX(null, "AUDIO_FORMAT_APTX", if (Build.VERSION.SDK_INT >= 26) 0x20000000U else 0x21000000U, 24, R.string.spk_encoding_aptx), // aosp since 26
        ENCODING_APTX_HD(null, "AUDIO_FORMAT_APTX_HD", if (Build.VERSION.SDK_INT >= 26) 0x21000000U else 0x22000000U, 24, R.string.spk_encoding_aptx_hd), // aosp since 26
        ENCODING_LDAC(null, "AUDIO_FORMAT_LDAC", 0x23000000U, 26, R.string.spk_encoding_ldac),
        ENCODING_MAT(null, "AUDIO_FORMAT_MAT", 0x24000000U, 28, R.string.spk_encoding_mat),
        ENCODING_AAC_LATM(null, "AUDIO_FORMAT_AAC_LATM", 0x25000000U, 29, R.string.spk_encoding_aac_latm),
        ENCODING_CELT(null, "AUDIO_FORMAT_CELT", 0x26000000U, 29, R.string.spk_encoding_celt),
        ENCODING_APTX_ADAPTIVE(null, "AUDIO_FORMAT_APTX_ADAPTIVE", 0x27000000U, 29, R.string.spk_encoding_aptx_adaptive),
        ENCODING_LHDC(null, "AUDIO_FORMAT_LHDC", 0x28000000U, 29, R.string.spk_encoding_lhdc),
        ENCODING_LHDC_LL(null, "AUDIO_FORMAT_LHDC_LL", 0x29000000U, 29, R.string.spk_encoding_lhdc_ll),
        ENCODING_APTX_TWSP(null, "AUDIO_FORMAT_APTX_TWSP", 0x2A000000U, 29, R.string.spk_encoding_aptx_twsp),
        ENCODING_LC3(null, "AUDIO_FORMAT_LC3", 0x2B000000U, 31, R.string.spk_encoding_lc3),
        ENCODING_MPEGH(null, "AUDIO_FORMAT_MPEGH", 0x2C000000U, 31, R.string.spk_encoding_mpegh),
        ENCODING_IEC60958(null, "AUDIO_FORMAT_IEC60958", 0x2D000000U, 31, R.string.spk_encoding_iec60958),
        ENCODING_DTS_UHD(null, "AUDIO_FORMAT_DTS_UHD", 0x2E000000U, 31, R.string.spk_encoding_dts_uhd),
        ENCODING_DRA(null, "AUDIO_FORMAT_DRA", 0x2F000000U, 31, R.string.spk_encoding_dra),
        ENCODING_APTX_ADAPTIVE_QLEA(null, "AUDIO_FORMAT_APTX_ADAPTIVE_QLEA", 0x30000000U, 33, R.string.spk_encoding_aptx_adaptive_qlea), // aosp since 34
        ENCODING_APTX_ADAPTIVE_R4(null, "AUDIO_FORMAT_APTX_ADAPTIVE_R4", 0x31000000U, 34, R.string.spk_encoding_aptx_adaptive_r4),
        ENCODING_DTS_HD_MA(null, "AUDIO_FORMAT_DTS_HD_MA", 0x32000000U, 34, R.string.spk_encoding_dts_hd_ma),
        ENCODING_AAC_MAIN(null, "AUDIO_FORMAT_AAC_MAIN", 0x4000001U, 21, R.string.spk_encoding_aac_main),
        ENCODING_AAC_SSR(null, "AUDIO_FORMAT_AAC_SSR", 0x4000004U, 21, R.string.spk_encoding_aac_ssr),
        ENCODING_AAC_LTP(null, "AUDIO_FORMAT_AAC_LTP", 0x4000008U, 21, R.string.spk_encoding_aac_ltp),
        ENCODING_AAC_SCALABLE(null, "AUDIO_FORMAT_AAC_SCALABLE", 0x4000020U, 21, R.string.spk_encoding_aac_scalable),
        ENCODING_AAC_ERLC(null, "AUDIO_FORMAT_AAC_ERLC", 0x4000040U, 21, R.string.spk_encoding_aac_erlc),
        ENCODING_AAC_LD(null, "AUDIO_FORMAT_AAC_LD", 0x4000080U, 21, R.string.spk_encoding_aac_ld),
        ENCODING_AAC_ADTS_MAIN(null, "AUDIO_FORMAT_AAC_ADTS_MAIN",0x1e000001U, 23, R.string.spk_encoding_aac_adts_main), // aosp since 26
        ENCODING_AAC_ADTS_LC(null, "AUDIO_FORMAT_AAC_ADTS_LC", 0x1e000002U, 23, R.string.spk_encoding_aac_adts_lc), // aosp since 26
        ENCODING_AAC_ADTS_SSR(null, "AUDIO_FORMAT_AAC_ADTS_SSR", 0x1e000004U, 23, R.string.spk_encoding_aac_adts_ssr), // aosp since 26
        ENCODING_AAC_ADTS_LTP(null, "AUDIO_FORMAT_AAC_ADTS_LTP", 0x1e000008U, 23, R.string.spk_encoding_aac_adts_ltp), // aosp since 26
        ENCODING_AAC_ADTS_HE_V1(null, "AUDIO_FORMAT_AAC_ADTS_HE_V1", 0x1e000010U, 23, R.string.spk_encoding_aac_adts_he_v1), // aosp since 26
        ENCODING_AAC_ADTS_SCALABLE(null, "AUDIO_FORMAT_AAC_ADTS_SCALABLE", 0x1e000020U, 23, R.string.spk_encoding_aac_adts_scalable), // aosp since 26
        ENCODING_AAC_ADTS_ERLC(null, "AUDIO_FORMAT_AAC_ADTS_ERLC", 0x1e000040U, 23, R.string.spk_encoding_aac_adts_erlc), // aosp since 26
        ENCODING_AAC_ADTS_LD(null, "AUDIO_FORMAT_AAC_ADTS_LD", 0x1e000080U, 23, R.string.spk_encoding_aac_adts_ld), // aosp since 26
        ENCODING_AAC_ADTS_HE_V2(null, "AUDIO_FORMAT_AAC_ADTS_HE_V2", 0x1e000100U, 23, R.string.spk_encoding_aac_adts_he_v2), // aosp since 26
        ENCODING_AAC_ADTS_ELD(null, "AUDIO_FORMAT_AAC_ADTS_ELD", 0x1e000200U, 23, R.string.spk_encoding_aac_adts_eld), // aosp since 26
        ENCODING_AAC_ADTS_XHE(null, "AUDIO_FORMAT_AAC_ADTS_XHE", 0x1e000300U, 28, R.string.spk_encoding_aac_adts_xhe),
        ENCODING_AAC_LATM_LC(null, "AUDIO_FORMAT_AAC_LATM_LC", 0x25000002U, 29, R.string.spk_encoding_aac_latm_lc),
        ENCODING_AAC_LATM_HE_V1(null, "AUDIO_FORMAT_AAC_LATM_HE_V1", 0x25000010U, 29, R.string.spk_encoding_aac_latm_he_v1),
        ENCODING_AAC_LATM_HE_V2(null, "AUDIO_FORMAT_AAC_LATM_HE_V2", 0x25000100U, 29, R.string.spk_encoding_aac_latm_he_v2),
        ENCODING_MAT_1_0(null, "AUDIO_FORMAT_MAT_1_0", 0x24000001U, 28, R.string.spk_encoding_mat_1_0),
        ENCODING_MAT_2_0(null, "AUDIO_FORMAT_MAT_2_0", 0x24000002U, 28, R.string.spk_encoding_mat_2_0),
        ENCODING_MAT_2_1(null, "AUDIO_FORMAT_MAT_2_1", 0x24000003U, 28, R.string.spk_encoding_mat_2_1),
        ENCODING_MPEGH_SUB_BL_L3(null, "AUDIO_FORMAT_MPEGH_SUB_BL_L3", 0x2C000013U, 31, R.string.spk_encoding_mpegh_sub_bl_l3),
        ENCODING_MPEHG_SUB_BL_L4(null, "AUDIO_FORMAT_MPEGH_SUB_BL_L4", 0x2C000014U, 31, R.string.spk_encoding_mpegh_sub_bl_l4),
        ENCODING_MPEGH_SUB_LC_L3(null, "AUDIO_FORMAT_MPEGH_SUB_LC_L3", 0x2C000023U, 31, R.string.spk_encoding_mpegh_sub_lc_l3),
        ENCODING_MPEGH_SUB_LC_L4(null, "AUDIO_FORMAT_MPEGH_SUB_LC_L4", 0x2C000024U, 31, R.string.spk_encoding_mpegh_sub_lc_l4);

        constructor(enc: Int?, enc2: String?, native: UInt?, firstSdk: Int, res: Int)
            : this(enc, enc2, native, firstSdk..1000, res)
        fun getString(context: Context) = context.getString(res)
        val isSupportedAsNative
            get() = sdkRange?.contains(Build.VERSION.SDK_INT) == true && native != null

        companion object {
            fun get(enc: Int) = Encoding.entries.find { it.enc == enc }
            fun get2(enc2: String) = Encoding.entries.find { it.enc2 == enc2 }
            fun getString(context: Context, enc: Int) = get(enc)?.getString(context)
            fun getStringFromString(context: Context, enc2: String) = get2(enc2)?.getString(context)
        }
    }

    @Parcelize
    enum class AudioQuality : Parcelable {
        UNKNOWN,    // Unable to determine quality
        LOSSY,      // Compressed lossy formats (MP3, AAC, OGG)
        CD,         // 16-bit/44.1kHz (Red Book) lossless
        HQ,         // other lossless formats that do not qualify as HD
        HD,         // 24-bit/44.1kHz or 24-bit/48kHz lossless
        HIRES       // 24-bit/88.2kHz+ (JAS Hi-Res Audio standard) lossless
    }

    @Parcelize
    enum class SpatialFormat : Parcelable {
        NONE,           // Mono
        STEREO,         // 2.0: Left, Right
        QUAD,           // 4.0: FL, FR, BL, BR (Quadraphonic)
        SURROUND_5_0,   // 5.0: FL, FR, FC, BL, BR
        SURROUND_5_1,   // 5.1: FL, FR, FC, LFE, BL, BR
        SURROUND_6_1,   // 6.1: FL, FR, FC, LFE, BC, SL, SR
        SURROUND_7_1,   // 7.1: FL, FR, FC, LFE, BL, BR, SL, SR
        DOLBY_AC4,      // Dolby AC-4
        DOLBY_AC3,      // Dolby Digital
        DOLBY_EAC3,     // Dolby Digital Plus
        DOLBY_EAC3_JOC, // Dolby Digital Plus with Joint Object Coding
        DOLBY_TRUEHD,   // Dolby TrueHD
        DTS,            // DTS
        DTS_EXPRESS,    // DTS Express
        DTS_HD,         // DTS-HD
        DTS_UHD,        // DTS-UHD Profile 2
        OTHER
    }

    @Parcelize
    data class AudioFormatInfo(
        val quality: AudioQuality,
        val sampleRate: Int?,
        val bitDepth: Int?,
        val isLossless: Boolean?,
        val sourceChannels: Int?,
        val bitrate: Long?,
        val mimeType: String?,
        val spatialFormat: SpatialFormat,
        val encoderPadding: Int?,
        val encoderDelay: Int?
    ) : Parcelable {
        override fun toString(): String {
            return """
                    Audio Format Details:
                    Quality Tier: $quality
                    Sample Rate: $sampleRate Hz
                    Bit Depth: $bitDepth bit
                    Source Channels: $sourceChannels
                    Lossless: $isLossless
                    Spatial Format: $spatialFormat
                    Codec: $mimeType
                    ${bitrate?.let { "Bitrate: ${it / 1000} kbps" } ?: ""}
                    Encoder Padding: $encoderPadding frames, $encoderDelay ms
                """.trimIndent()
        }
    }

    data class AudioFormats(
        val downstreamFormat: List<Pair<Int, Format>>?, val audioSinkInputFormat: Format?,
        val audioTrackInfo: AudioTrackInfo?, val halFormat: AfFormatInfo?,
        val btCodecInfo: BtCodecInfo?
    ) {
        fun prettyToString(context: Context): String? {
            if (downstreamFormat == null || audioSinkInputFormat == null || audioTrackInfo == null)
                return null
            // TODO localization and handle nulls in data nicely
            return StringBuilder().apply {
                downstreamFormat.forEachIndexed { i, it ->
                    append("== Downstream format [$i] ==\n")
                    prettyPrintFormat(context, it.second)
                    append("\n")
                }
                append("== Audio sink input format ==\n")
                prettyPrintFormat(context, audioSinkInputFormat)
                append("\n")
                append("== Audio track format ==\n")
                prettyPrintAudioTrackInfo(context, audioTrackInfo)
                if (halFormat != null) {
                    append("Policy port/track ID: ${halFormat.policyPortId} (session: ${halFormat.audioSessionId})\n")
                    append("Granted flags: ${mixPortFlagsToString(context, halFormat.grantedFlags)}\n")
                    append("Server flags: ${mixPortFlagsToString(context, halFormat.afTrackFlags)}\n")
                } else
                    append("(some data is not available)\n")
                append("\n")
                append("== Audio HAL format ==\n")
                if (halFormat == null)
                    append("(no data available)")
                else
                    prettyPrintAfFormatInfo(context, halFormat)
                append("\n")
                append("== Playback device ==\n")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && halFormat != null) {
                    append("Device name: ${halFormat.routedDeviceName} (ID: ${halFormat.routedDeviceId})\n")
                    append("Device type: ${halFormat.routedDeviceType?.let { audioDeviceTypeToString(context, it) }}\n")
                } else {
                    append("(some data is not available)\n")
                }
                if (halFormat?.isBluetoothOffload != null) {
                    if (halFormat.routedDeviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                        append("A2DP offload: ${halFormat.isBluetoothOffload}\n")
                    } else {
                        append("LE Audio offload: ${halFormat.isBluetoothOffload}\n")
                    }
                }
                if (halFormat?.routedDeviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    if (btCodecInfo != null)
                        prettyPrintBtCodecInfo(context, btCodecInfo)
                    else
                        append("(some data about Bluetooth is not available)\n")
                }
            }.toString()
        }

        private fun StringBuilder.prettyPrintFormat(context: Context, format: Format) {
            append("Sample rate: ")
            if (format.sampleRate != Format.NO_VALUE) {
                append(format.sampleRate)
                append(" Hz\n")
            } else {
                append("Not applicable to this format\n")
            }

            append("Bit depth: ")
            val bitDepth = try {
                Util.getBitDepth(format.pcmEncoding)
            } catch (_: IllegalArgumentException) {
                null
            }
            if (bitDepth != null) {
                append(bitDepth)
                append(" bits (")
                append(Encoding.getString(context, format.pcmEncoding))
                append(")\n")
            } else {
                append("Not applicable to this format\n")
            }

            append("Channel count: ")
            if (format.channelCount != Format.NO_VALUE) {
                append(format.channelCount)
                append(" channels\n")
            } else {
                append("Not applicable to this format\n")
            }

            if (format.sampleMimeType != MimeTypes.AUDIO_RAW) {
                append("Bitrate: ")
                if (format.bitrate != Format.NO_VALUE) {
                    append("~")
                    append(format.bitrate.toLong() / 1000)
                    append("kbps\n")
                } else {
                    append("Unknown\n")
                }
            }

            if (format.sampleMimeType != null) {
                append("MIME type: ")
                append(format.sampleMimeType)
                append("\n")
            }

            if (format.containerMimeType != null) {
                append("Container MIME type: ")
                append(format.containerMimeType)
                append("\n")
            }
        }

        private fun StringBuilder.prettyPrintAudioTrackInfo(context: Context, format: AudioTrackInfo) {
            append("Channel config: ${channelConfigToString(context, format.channelConfig)}\n")
            append("Sample rate: ${format.sampleRateHz} Hz\n")
            append(
                "Audio format: ${
                    Encoding.getString(context, format.encoding)
                        ?: context.getString(R.string.spk_encoding_unknown_d, format.encoding)
                }\n"
            )
            append("Offload: ${format.offload}\n")
        }

        private fun StringBuilder.prettyPrintAfFormatInfo(context: Context, format: AfFormatInfo) {
            append("Mix port: ${format.mixPortName} (ID: ${format.mixPortId})\n")
            append("Mix port flags: ${mixPortFlagsToString(context, format.mixPortFlags)}")
            if (format.mixPortFast != null)
                append(" (fast: ${format.mixPortFast})")
            append("\nMix port hw module ID: ${format.mixPortHwModule}\n")
            append("I/O handle: ${format.ioHandle}\n")
            append("Sample rate: ${format.sampleRateHz} Hz\n")
            append(
                "Audio format: ${
                    format.audioFormat?.let { Encoding.getStringFromString(context, it) }
                        ?: context.getString(R.string.spk_encoding_unknown, format.audioFormat)
                }\n")
            append("Channel count: ${format.channelCount}\n")
            append("Channel mask: ${format.channelMask}\n")
        }

        private fun StringBuilder.prettyPrintBtCodecInfo(context: Context, format: BtCodecInfo) {
            append("Bluetooth codec: ${format.codec}\n")
            if (format.quality != null) {
                append("Codec quality: ${format.quality}\n")
            }
            append("Sample rate: ${format.sampleRateHz} Hz\n")
            if (format.bitsPerSample != -1) {
                append("Bit depth: ${format.bitsPerSample} bits\n")
            }
            append("Channel config: ${channelConfigToString(context, format.channelConfig)}\n")
        }
    }

    fun detectAudioFormat(
        f: AudioFormats?
    ): AudioFormatInfo? {
        if (f == null) return null
        val formats = f.downstreamFormat?.filter { it.first == C.TRACK_TYPE_AUDIO }
        if (formats?.size != 1) return null
        val format = formats.first().second
        val sampleRate = format.sampleRate.takeIf { it != Format.NO_VALUE }
        val bitDepth = try {
            Util.getBitDepth(format.pcmEncoding)
        } catch (_: IllegalArgumentException) {
            null
        }
        val isLossless = isLosslessFormat(format.sampleMimeType)
        val bitrate = if (isLossless != true)
            format.bitrate.takeIf { it != Format.NO_VALUE }?.toLong() else null
        val spatialFormat = detectSpatialFormat(format)
        val sourceChannels = format.channelCount.takeIf { it != Format.NO_VALUE }

        val quality = determineQualityTier(
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            isLossless = isLossless,
            mimeType = format.sampleMimeType
        )

        return AudioFormatInfo(
            quality = quality,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            isLossless = isLossless,
            sourceChannels = sourceChannels,
            bitrate = bitrate,
            mimeType = format.sampleMimeType,
            spatialFormat = spatialFormat,
            encoderPadding = format.encoderPadding.takeIf { it != Format.NO_VALUE },
            encoderDelay = format.encoderDelay.takeIf { it != Format.NO_VALUE }
        )
    }

    private fun isLosslessFormat(mimeType: String?): Boolean? = when (mimeType) {
        MimeTypes.AUDIO_FLAC,
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_WAV,
        MimeTypes.AUDIO_RAW,
        MimeTypes.AUDIO_TRUEHD,
        MimeTypes.AUDIO_MIDI,
        MimeTypes.AUDIO_EXOPLAYER_MIDI -> true

        // TODO distinguish lossless DTS-HD MA vs other lossy DTS-HD encoding schemes
	    //  https://github.com/androidx/media/issues/2487
        MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_X -> null

        else -> false
    }

    private fun detectSpatialFormat(format: Format): SpatialFormat {
        val mimeFormat = when (format.sampleMimeType) {
            MimeTypes.AUDIO_AC3 -> SpatialFormat.DOLBY_AC3
            MimeTypes.AUDIO_E_AC3 -> SpatialFormat.DOLBY_EAC3
            MimeTypes.AUDIO_E_AC3_JOC -> SpatialFormat.DOLBY_EAC3_JOC
            MimeTypes.AUDIO_AC4 -> SpatialFormat.DOLBY_AC4
            MimeTypes.AUDIO_TRUEHD -> SpatialFormat.DOLBY_TRUEHD
            MimeTypes.AUDIO_DTS -> SpatialFormat.DTS
            MimeTypes.AUDIO_DTS_EXPRESS -> SpatialFormat.DTS_EXPRESS
            MimeTypes.AUDIO_DTS_HD -> SpatialFormat.DTS_HD
            MimeTypes.AUDIO_DTS_X -> SpatialFormat.DTS_UHD
            else -> null
        }

        if (mimeFormat != null) return mimeFormat

        // Standard multichannel formats
        // TODO can we just go by channel count? isn't there any way to distinguish QUAD
        //  from QUAD_SIDE?
        //  answer: until https://github.com/androidx/media/issues/1471 happens we cannot
        return when (format.channelCount) {
            1 -> SpatialFormat.NONE          // Mono
            2 -> SpatialFormat.STEREO        // Standard stereo
            4 -> SpatialFormat.QUAD          // Quadraphonic
            5 -> SpatialFormat.SURROUND_5_0  // 5.0 surround
            6 -> SpatialFormat.SURROUND_5_1  // 5.1 surround
            7 -> SpatialFormat.SURROUND_6_1  // 6.1 surround
            8 -> SpatialFormat.SURROUND_7_1  // 7.1 surround
            else -> SpatialFormat.OTHER
        }
    }

    fun mixPortFlagsToString(context: Context, flags: Int?): String {
        if (flags == null) {
            return context.getString(R.string.mix_port_flag_unknown)
        }
        if (flags == 0x0) { // AUDIO_OUTPUT_FLAG_NONE
            return context.getString(R.string.mix_port_flag_none)
        }
        val str = mutableListOf<String>()
        if ((flags and 0x1) != 0) {
            str += context.getString(R.string.mix_port_flag_direct)
        }
        if ((flags and 0x2) != 0) {
            str += context.getString(R.string.mix_port_flag_primary)
        }
        if ((flags and 0x4) != 0) {
            str += context.getString(R.string.mix_port_flag_fast)
        }
        if ((flags and 0x8) != 0) {
            str += context.getString(R.string.mix_port_flag_deep_buffer)
        }
        if ((flags and 0x10) != 0) {
            str += context.getString(R.string.mix_port_flag_compress_offload)
        }
        if ((flags and 0x20) != 0) {
            str += context.getString(R.string.mix_port_flag_non_blocking)
        }
        if ((flags and 0x40) != 0) {
            str += context.getString(R.string.mix_port_flag_hw_av_sync)
        }
        if ((flags and 0x80) != 0) {
            str += context.getString(R.string.mix_port_flag_tts)
        }
        if ((flags and 0x100) != 0) {
            str += context.getString(R.string.mix_port_flag_raw)
        }
        if ((flags and 0x200) != 0) {
            str += context.getString(R.string.mix_port_flag_sync)
        }
        if ((flags and 0x400) != 0) {
            str += context.getString(R.string.mix_port_flag_iec958_nonaudio)
        }
        if ((flags and 0x2000) != 0) {
            str += context.getString(R.string.mix_port_flag_direct_pcm)
        }
        if ((flags and 0x4000) != 0) {
            str += context.getString(R.string.mix_port_flag_mmap_noirq)
        }
        if ((flags and 0x8000) != 0) {
            str += context.getString(R.string.mix_port_flag_voip_rx)
        }
        if ((flags and 0x10000) != 0) {
            str += context.getString(R.string.mix_port_flag_incall_music)
        }
        if ((flags and 0x20000) != 0) {
            str += context.getString(R.string.mix_port_flag_gapless_offload)
        }
        if ((flags and 0x40000) != 0) {
            str += context.getString(R.string.mix_port_flag_spatializer)
        }
        if ((flags and 0x80000) != 0) {
            str += context.getString(R.string.mix_port_flag_ultrasound)
        }
        if ((flags and 0x100000) != 0) {
            str += context.getString(R.string.mix_port_flag_bit_perfect)
        }
        return str.joinToString(", ")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun audioDeviceTypeToString(context: Context, type: Int?) =
        when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> context.getString(R.string.device_type_bluetooth_a2dp)
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> context.getString(R.string.device_type_builtin_speaker)
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> context.getString(R.string.device_type_wired_headset)
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> context.getString(R.string.device_type_wired_headphones)
            AudioDeviceInfo.TYPE_HDMI -> context.getString(R.string.device_type_hdmi)
            AudioDeviceInfo.TYPE_USB_DEVICE -> context.getString(R.string.device_type_usb_device)
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> context.getString(R.string.device_type_usb_accessory)
            AudioDeviceInfo.TYPE_DOCK -> context.getString(
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) R.string.device_type_dock_digital
                else R.string.device_type_dock
            )

            AudioDeviceInfo.TYPE_DOCK_ANALOG -> context.getString(R.string.device_type_dock_analog)
            AudioDeviceInfo.TYPE_USB_HEADSET -> context.getString(R.string.device_type_usb_headset)
            AudioDeviceInfo.TYPE_HEARING_AID -> context.getString(R.string.device_type_hearing_aid)
            AudioDeviceInfo.TYPE_BLE_HEADSET -> context.getString(R.string.device_type_ble_headset)
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> context.getString(R.string.device_type_ble_broadcast)
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> context.getString(R.string.device_type_ble_speaker)
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> context.getString(R.string.device_type_line_digital)
            AudioDeviceInfo.TYPE_LINE_ANALOG -> context.getString(R.string.device_type_line_analog)
            AudioDeviceInfo.TYPE_AUX_LINE -> context.getString(R.string.device_type_aux_line)
            AudioDeviceInfo.TYPE_HDMI_ARC -> context.getString(R.string.device_type_hdmi_arc)
            AudioDeviceInfo.TYPE_HDMI_EARC -> context.getString(R.string.device_type_hdmi_earc)
            else -> {
                Log.w("AudioFormatDetector", "unknown device type $type")
                context.getString(R.string.device_type_unknown)
            }
        }

	private fun determineQualityTier(
        sampleRate: Int?,
        bitDepth: Int?,
        isLossless: Boolean?,
        mimeType: String?
    ): AudioQuality = when {
        isLossless == false -> AudioQuality.LOSSY

        // Hi-Res: 24bit+ and 96kHz+
        bitDepth != null && sampleRate != null &&
                bitDepth >= 24 && sampleRate >= 88200 -> AudioQuality.HIRES

        // HD: >16bit at standard rates OR 16bit at high rates
        bitDepth != null && sampleRate != null &&
                (bitDepth > 16 || sampleRate > 48000) -> AudioQuality.HD

        // CD: 16bit at 44.1kHz
        bitDepth == 16 && sampleRate == 44100 -> AudioQuality.CD

        // Fallback for non-standard combinations
        bitDepth != null && sampleRate != null -> AudioQuality.HQ

        mimeType == MimeTypes.AUDIO_EXOPLAYER_MIDI ||
                mimeType == MimeTypes.AUDIO_MIDI -> AudioQuality.HQ

        else -> AudioQuality.UNKNOWN
    }
}