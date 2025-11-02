package org.akanework.gramophone.logic.utils

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Log
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.Util
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.extractor.mp3.Mp3InfoReplayGain
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

sealed class ReplayGainUtil {
	enum class Mode {
		None, Track, Album
	}
	data class Rva2(val identification: String, val channels: List<Channel>) : ReplayGainUtil() {
		enum class ChannelEnum {
			Other,
			MasterVolume,
			FrontRight,
			FrontLeft,
			BackRight,
			BackLeft,
			FrontCenter,
			BackCenter,
			Subwoofer
		}
		data class Channel(val channel: ChannelEnum, val volumeAdjustment: Float,
		                   val peakVolume: Float?)
	}
	data class Rvad(val channels: List<Channel>) : ReplayGainUtil() {
		enum class ChannelEnum {
			FrontLeft,
			FrontRight,
			BackLeft,
			BackRight,
			Center,
			Bass
		}
		data class Channel(val channel: ChannelEnum, val volumeAdjustment: Float,
		                   val peakVolume: Float?)
	}
	sealed class Txxx : ReplayGainUtil()
	data class TxxxTrackGain(val value: Float) : Txxx()
	data class R128TrackGain(val value: Float) : Txxx()
	data class TxxxTrackPeak(val value: Float) : Txxx()
	data class TxxxAlbumGain(val value: Float) : Txxx()
	data class R128AlbumGain(val value: Float) : Txxx()
	data class TxxxAlbumPeak(val value: Float) : Txxx()
	data class SoundCheck(val gainL: Float, val gainR: Float, val gainAltL: Float,
	                      val gainAltR: Float, val unk1: Int, val unk2: Int, val peakL: Float,
	                      val peakR: Float, val unk3: Int, val unk4: Int) : ReplayGainUtil()
	data class Mp3Info(val peak: Float, val field1Name: Byte, val field1Originator: Byte,
	                val field1Value: Float, val field2Name: Byte, val field2Originator: Byte,
	                val field2Value: Float) : ReplayGainUtil() {
		constructor(i: Mp3InfoReplayGain) : this(i.peak, i.field1Name,
			i.field1Originator, i.field1Value, i.field2Name,
			i.field2Originator, i.field2Value)
	}
	data class Rgad(val peak: Float, val field1Name: Byte, val field1Originator: Byte,
	                val field1Value: Float, val field2Name: Byte, val field2Originator: Byte,
	                val field2Value: Float) : ReplayGainUtil()
	sealed class RgInfo(val value: Float) {
		class TrackGain(gain: Float) : RgInfo(gain)
		class TrackPeak(peak: Float) : RgInfo(peak)
		class AlbumGain(gain: Float) : RgInfo(gain)
		class AlbumPeak(peak: Float) : RgInfo(peak)
	}
	data class ReplayGainInfo(val trackGain: Float?, val trackPeak: Float?, val albumGain: Float?,
	                          val albumPeak: Float?)
	companion object {
		private const val TAG = "ReplayGainUtil"
		const val RATIO = 2f
		const val TAU_ATTACK = 0.0014f
		const val TAU_RELEASE = 0.093f

		private fun adjustVolume(bytes: ByteArray, sign: Boolean): Float {
			val peak = BigInteger(bytes)
				.let { if (sign) it.multiply(BigInteger.valueOf(-1)) else it }
				.toLong()
			// iTunes uses a range of -255 to 255 to be -100% (silent) to 100% (+6dB)
			return if (peak == -255L) -96f else 20f * ln((peak + 255) / 255f) / ln(10f)
		}

		private fun adjustPeak(bytes: ByteArray, bitDepth: Int): Float? {
			val peak = BigInteger(bytes)
			if (peak.toInt() == 0) return null
			val min = BigDecimal(BigInteger.ONE.shiftLeft(bitDepth - 1).negate())
			val max = BigInteger.ONE.shiftLeft(bitDepth - 1).subtract(BigInteger.ONE)
			val range = BigDecimal(max).subtract(min)
			return BigDecimal(peak).subtract(min)
				.divide(range, MathContext.DECIMAL128)
				.multiply(BigDecimal(2))
				.subtract(BigDecimal(1)).toFloat()
		}

		private fun parseRva2(frame: BinaryFrame, bitDepth: Int): Rva2 {
			if (frame.id != "RVA2" && frame.id != "XRV" && frame.id != "XRVA")
				throw IllegalStateException("parseRva2() but frame isn't RVA2, it's $frame")
			val frame = ParsableByteArray(frame.data)
			val identificationLen = indexOfZeroByte(frame.data)
			val identification = String(frame.data, 0, identificationLen,
				Charsets.ISO_8859_1)
			frame.skipBytes(identificationLen + 1)
			val channels = arrayListOf<Rva2.Channel>()
			while (frame.bytesLeft() > 0) {
				val channel = frame.readUnsignedByte()
				val volumeAdjustment = frame.readShort() / 512f
				val len = ceil(frame.readUnsignedByte() / 8f).toInt()
				val peakBytes = ByteArray(len)
				frame.readBytes(peakBytes, 0, len)
				channels += Rva2.Channel(Rva2.ChannelEnum.entries[channel],
					volumeAdjustment, adjustPeak(peakBytes, bitDepth))
			}
			return Rva2(identification, channels)
		}

		private fun parseRvad(frame: BinaryFrame, bitDepth: Int): Rvad {
			if (frame.id != "RVAD" && frame.id != "RVA")
				throw IllegalStateException("parseRvad() but frame isn't RVAD, it's $frame")
			val frame = ParsableByteArray(frame.data)
			val signs = frame.readUnsignedByte()
			val signFR = signs and 1 == 0
			val signFL = (signs shr 1) and 1 == 0
			val signBR = (signs shr 2) and 1 == 0
			val signBL = (signs shr 3) and 1 == 0
			val signC = (signs shr 4) and 1 == 0
			val signB = (signs shr 5) and 1 == 0
			val len = ceil(frame.readUnsignedByte() / 8f).toInt()
			val buf = ByteArray(len)
			val channels = arrayListOf<Rvad.Channel>()
			frame.readBytes(buf, 0, len)
			val volumeAdjFR = adjustVolume(buf, signFR)
			frame.readBytes(buf, 0, len)
			val volumeAdjFL = adjustVolume(buf, signFL)
			val peakVolFR: Float?
			val peakVolFL: Float?
			if (frame.bytesLeft() > 0) {
				frame.readBytes(buf, 0, len)
				peakVolFR = adjustPeak(buf, bitDepth)
				frame.readBytes(buf, 0, len)
				peakVolFL = adjustPeak(buf, bitDepth)
			} else {
				peakVolFR = null
				peakVolFL = null
			}
			channels += Rvad.Channel(Rvad.ChannelEnum.FrontRight,
				volumeAdjFR, peakVolFR)
			channels += Rvad.Channel(Rvad.ChannelEnum.FrontLeft,
				volumeAdjFL, peakVolFL)
			if (frame.bytesLeft() > 0) {
				frame.readBytes(buf, 0, len)
				val volumeAdjBR = adjustVolume(buf, signBR)
				frame.readBytes(buf, 0, len)
				val volumeAdjBL = adjustVolume(buf, signBL)
				val peakVolBR: Float?
				val peakVolBL: Float?
				if (frame.bytesLeft() > 0) {
					frame.readBytes(buf, 0, len)
					peakVolBR = adjustPeak(buf, bitDepth)
					frame.readBytes(buf, 0, len)
					peakVolBL = adjustPeak(buf, bitDepth)
				} else {
					peakVolBR = null
					peakVolBL = null
				}
				channels += Rvad.Channel(Rvad.ChannelEnum.BackRight,
					volumeAdjBR, peakVolBR)
				channels += Rvad.Channel(Rvad.ChannelEnum.BackLeft,
					volumeAdjBL, peakVolBL)
				if (frame.bytesLeft() > 0) {
					frame.readBytes(buf, 0, len)
					val volumeAdjC = adjustVolume(buf, signC)
					val peakVolC: Float?
					if (frame.bytesLeft() > 0) {
						frame.readBytes(buf, 0, len)
						peakVolC = adjustPeak(buf, bitDepth)
					} else {
						peakVolC = null
					}
					channels += Rvad.Channel(Rvad.ChannelEnum.Center,
						volumeAdjC, peakVolC)
					if (frame.bytesLeft() > 0) {
						frame.readBytes(buf, 0, len)
						val volumeAdjB = adjustVolume(buf, signB)
						val peakVolB: Float?
						if (frame.bytesLeft() > 0) {
							frame.readBytes(buf, 0, len)
							peakVolB = adjustPeak(buf, bitDepth)
						} else {
							peakVolB = null
						}
						channels += Rvad.Channel(Rvad.ChannelEnum.Bass,
							volumeAdjB, peakVolB)
					}
				}
			}
			return Rvad(channels)
		}

		private fun parseTxxxReference(description: String?, values: List<String>): Float? {
			val description = description?.uppercase()
			return if (description == "REPLAYGAIN_REFERENCE_LOUDNESS") {
				var value = values.firstOrNull()?.trim()
				if (value?.endsWith(" LUFS", ignoreCase = true) == true) {
					value = value.dropLast(5)
					value.replace(',', '.').toFloatOrNull()?.let { -18f - it }
				} else null // we can't parse dB (SPL) values because idk the conversion formula
			} else null
		}

		private fun parseTxxx(description: String?, values: List<String>, diff: Float): Txxx? {
			return when (val description = description?.uppercase()) {
				"REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN", "RVA", "RVA_ALBUM", "RVA_RADIO",
				"RVA_MIX", "RVA_AUDIOPHILE", "RVA_USER", "REPLAY GAIN",
				"MEDIA JUKEBOX: REPLAY GAIN", "MEDIA JUKEBOX: ALBUM GAIN" -> {
					var value = values.firstOrNull()?.trim()
					if (value?.endsWith(" dB", ignoreCase = true) == true
						|| value?.endsWith(" LU", ignoreCase = true) == true) {
						value = value.dropLast(3)
					}
					value?.replace(',', '.')?.toFloatOrNull()?.let {
						val diff = if (description == "REPLAYGAIN_TRACK_GAIN" ||
							description == "REPLAYGAIN_ALBUM_GAIN") diff else 0f
						if (description.contains("ALBUM") ||
							description == "RVA_AUDIOPHILE" ||
							description == "RVA_USER") TxxxAlbumGain(it + diff)
						else TxxxTrackGain(it + diff)
					}
				}
				"REPLAYGAIN_TRACK_PEAK", "REPLAYGAIN_ALBUM_PEAK", "PEAK LEVEL",
				"MEDIA JUKEBOX: PEAK LEVEL" -> {
					val value = values.firstOrNull()?.trim()
					value?.replace(',', '.')?.toFloatOrNull()?.let {
						if (description == "REPLAYGAIN_ALBUM_PEAK") TxxxAlbumPeak(it)
						else TxxxTrackPeak(it)
					}
				}
				"R128_TRACK_GAIN", "R128_ALBUM_GAIN" -> {
					val value = values.first().trim()
					value.replace(',', '.').toFloatOrNull()?.let {
						if (description == "R128_ALBUM_GAIN") R128AlbumGain(it / 256f)
						else R128TrackGain(it / 256f)
					}
				}
				else -> null
			}
		}

		private fun parseRgad(frame: BinaryFrame): Rgad {
			if (frame.id != "RGAD" && frame.id != "RGA")
				throw IllegalStateException("parseRgad() but frame isn't RGAD, it's $frame")
			val frame = ParsableByteArray(frame.data)
			val peak = frame.readFloat()
			val field1 = frame.readShort()
			val field1Name = ((field1.toInt() shr 13) and 7).toByte()
			val field1Originator = ((field1.toInt() shr 10) and 7).toByte()
			val field1Value =
				((field1.toInt() and 0x1ff) * (if ((field1.toInt() and 0x200) != 0) -1 else 1)) / 10f
			val field2: Short = frame.readShort()
			val field2Name = ((field2.toInt() shr 13) and 7).toByte()
			val field2Originator = ((field2.toInt() shr 10) and 7).toByte()
			val field2Value =
				((field2.toInt() and 0x1ff) * (if ((field2.toInt() and 0x200) != 0) -1 else 1)) / 10f
			return Rgad(
				peak, field1Name, field1Originator, field1Value, field2Name, field2Originator,
				field2Value
			)
		}

		private fun parseITunNORM(text: String): SoundCheck? {
			val soundcheck = try {
				text.trim().split(' ').map {
					it.hexToInt(HexFormat {
						upperCase = true
					})
				}
			} catch (_: IllegalArgumentException) {
				return null
			}
			if (soundcheck.size < 9)
				return null
			val gainL = log10(soundcheck[0] / 1000.0f) * -10
			val gainR = log10(soundcheck[1] / 1000.0f) * -10
			val gainAltL = log10(soundcheck[2] / 2500.0f) * -10
			val gainAltR = log10(soundcheck[3] / 2500.0f) * -10
			val unk1 = soundcheck[4]
			val unk2 = soundcheck[5]
			val peakL = soundcheck[6] / 32768.0f
			val peakR = soundcheck[7] / 32768.0f
			val unk3 = soundcheck[8]
			val unk4 = soundcheck[9]
			return SoundCheck(gainL, gainR, gainAltL, gainAltR, unk1, unk2, peakL, peakR, unk3, unk4)
		}

		fun parse(inputFormat: Format?): ReplayGainInfo {
			if (inputFormat?.metadata == null) {
				return ReplayGainInfo(null, null, null, null)
			}
			val metadata = arrayListOf<ReplayGainUtil>()
			val pcmEncoding = inputFormat.pcmEncoding.takeIf { it != Format.NO_VALUE }
				?: when (inputFormat.sampleMimeType) { // for offload, fill in likely values
					MimeTypes.AUDIO_MPEG,
					MimeTypes.AUDIO_VORBIS,
					MimeTypes.AUDIO_AAC,
					MimeTypes.AUDIO_OPUS -> C.ENCODING_PCM_16BIT
					else -> Format.NO_VALUE
				}
			inputFormat.metadata!!.getMatchingEntries(InternalFrame::class.java)
			{ it.domain == "com.apple.iTunes" &&
					it.description.startsWith("REPLAYGAIN_", ignoreCase = true) }
				.let {
					val diff = it.firstNotNullOfOrNull { frame ->
						try {
							parseTxxxReference(frame.description, listOf(frame.text))
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					} ?: 0f
					metadata.addAll(it.mapNotNull { frame ->
						try {
							parseTxxx(frame.description, listOf(frame.text), diff)
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					})
				} // agreed standard for ReplayGain in MP$
			inputFormat.metadata!!.getMatchingEntries(VorbisComment::class.java)
			{ it.key.startsWith("REPLAYGAIN_", ignoreCase = true) /* OggVorbis/Flac */
					|| it.key.startsWith("R128_", ignoreCase = true) /* OggOpus */
					|| it.key.equals("REPLAY GAIN", ignoreCase = true) /* JRiver */
					|| it.key.equals("PEAK LEVEL", ignoreCase = true) /* also JRiver */ }
				.let {
					val diff = it.firstNotNullOfOrNull { frame ->
						try {
							parseTxxxReference(frame.key, listOf(frame.value))
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					} ?: 0f
					metadata.addAll(it.mapNotNull { frame ->
						try {
							parseTxxx(frame.key, listOf(frame.value), diff)
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					})
				}
			inputFormat.metadata!!.getMatchingEntries(TextInformationFrame::class.java)
			{ (it.id == "TXXX" || it.id == "TXX") && (
					it.description?.startsWith("REPLAYGAIN_", ignoreCase = true) == true ||
							// MEDIA JUKEBOX = JRiver
					it.description?.equals("MEDIA JUKEBOX: REPLAY GAIN", ignoreCase = true) == true ||
					it.description?.equals("MEDIA JUKEBOX: ALBUM GAIN", ignoreCase = true) == true ||
					it.description?.equals("MEDIA JUKEBOX: PEAK LEVEL", ignoreCase = true) == true) }
				.let {
					val diff = it.firstNotNullOfOrNull { frame ->
						try {
							parseTxxxReference(frame.description, frame.values)
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					} ?: 0f
					metadata.addAll(it.mapNotNull { frame ->
						try {
							parseTxxx(frame.description, frame.values, diff)
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					})
				} // Modern-day ReplayGain for ID3
			inputFormat.metadata!!.getMatchingEntries(CommentFrame::class.java)
			{ it.description.startsWith("RVA", ignoreCase = true) }
				.let {
					metadata.addAll(it.mapNotNull { frame ->
						try {
							parseTxxx(frame.description, listOf(frame.text), 0f)
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					})
				} // proposed by author of and supported in mpg123
			if (pcmEncoding != Format.NO_VALUE) {
				inputFormat.metadata!!.getMatchingEntries(BinaryFrame::class.java)
				{ it.id == "RVA2" || it.id == "XRV" || it.id == "XRVA" }.let {
					metadata.addAll(it.mapNotNull { frame ->
						try {
							parseRva2(frame, Util.getBitDepth(pcmEncoding))
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					})
				}
			} // ID3v2.4 RVA2 frame and backport to ID3v2.2/2.3 by normalize
			val iTunNorm = inputFormat.metadata!!.getMatchingEntries(CommentFrame::class.java)
			{ it.description == "iTunNORM" }
				.let {
					it.mapNotNull { frame ->
						try {
							parseITunNORM(frame.text)
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					}
				} // iTunes SoundCheck (MP3)
			if (pcmEncoding != Format.NO_VALUE) {
				inputFormat.metadata!!.getMatchingEntries(BinaryFrame::class.java)
				{ it.id == "RVAD" || it.id == "RVA" }.let {
					val out = it.mapNotNull { frame ->
						try {
							parseRvad(frame,
								Util.getBitDepth(pcmEncoding))
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					}
					if (out.isNotEmpty()) {
						// see https://bugs-archive.lyrion.org/bug-6890.html#c13
						// RVAD/RVA + iTunNORM should be combined
						metadata.addAll(out.map { frame ->
							frame.copy(channels = frame.channels.map { ch ->
								ch.copy(volumeAdjustment = ch.volumeAdjustment + (iTunNorm
									.firstOrNull()?.let { f -> max(f.gainL, f.gainR) } ?: 0f))
							})
						})
					} else {
						metadata.addAll(iTunNorm)
					}
				}
			} // ID3v2.2 RVA frame and ID3v2.3 RVAD frame
			else {
				metadata.addAll(iTunNorm)
			}
			inputFormat.metadata!!.getMatchingEntries(BinaryFrame::class.java)
			{ it.id == "RGAD" }.let { metadata.addAll(it.mapNotNull { frame ->
				try {
					parseRgad(frame)
				} catch (e: Exception) {
					Log.e(TAG, "failed to parse $frame", e)
					null
				}
			}) } // Classic ReplayGain proposed ID3 tag
			inputFormat.metadata!!.getEntriesOfType(Mp3InfoReplayGain::class.java)
				.let { metadata.addAll(it.map { info -> Mp3Info(info) }) } // LAME
			inputFormat.metadata!!.getMatchingEntries(InternalFrame::class.java)
			{ it.domain == "com.apple.iTunes" && it.description == "iTunNORM" }
				.let {
					metadata.addAll(it.mapNotNull { frame ->
						try {
							parseITunNORM(frame.text)
						} catch (e: Exception) {
							Log.e(TAG, "failed to parse $frame", e)
							null
						}
					})
				} // iTunes SoundCheck (MP4)
			val infos = metadata.flatMap {
				when (it) {
					is Mp3Info -> {
						val out = mutableListOf<RgInfo>()
						if ((it.field1Name == 1.toByte() || it.field1Name == 2.toByte()
									|| it.field2Name == 1.toByte() || it.field2Name == 2.toByte())
							&& it.peak != 0f)
							out += RgInfo.TrackPeak(it.peak)
						if (it.field1Name == 1.toByte()) {
							out += RgInfo.TrackGain(it.field1Value)
						} else if (it.field1Name == 2.toByte()) {
							out.add(RgInfo.AlbumGain(it.field1Value))
						}
						if (it.field2Name == 1.toByte()) {
							out += RgInfo.TrackGain(it.field2Value)
						} else if (it.field2Name == 2.toByte()) {
							out.add(RgInfo.AlbumGain(it.field2Value))
						}
						out
					}
					is Rgad -> {
						val out = mutableListOf<RgInfo>()
						if ((it.field1Name == 1.toByte() || it.field1Name == 2.toByte()
									|| it.field2Name == 1.toByte() || it.field2Name == 2.toByte())
							&& it.peak != 0f)
							out += RgInfo.TrackPeak(it.peak)
						if (it.field1Name == 1.toByte()) {
							out += RgInfo.TrackGain(it.field1Value)
						} else if (it.field1Name == 2.toByte()) {
							out.add(RgInfo.AlbumGain(it.field1Value))
						}
						if (it.field2Name == 1.toByte()) {
							out += RgInfo.TrackGain(it.field2Value)
						} else if (it.field2Name == 2.toByte()) {
							out.add(RgInfo.AlbumGain(it.field2Value))
						}
						out
					}
					is Rva2 -> {
						val out = mutableListOf<RgInfo>()
						if (it.identification.startsWith("track", ignoreCase = true)
							|| it.identification.startsWith("mix", ignoreCase = true)
							|| it.identification.startsWith("radio", ignoreCase = true)) {
							val masterVolume = it.channels.find { ch ->
								ch.channel == Rva2.ChannelEnum.MasterVolume }
							if (masterVolume != null) {
								out += RgInfo.TrackGain(masterVolume.volumeAdjustment)
								if (masterVolume.peakVolume != null)
									out += RgInfo.TrackPeak(masterVolume.peakVolume)
								else
									it.channels.maxOf { ch -> ch.peakVolume ?: 0f }
										.takeIf { peak -> peak != 0f }?.let { peak ->
											out += RgInfo.TrackPeak(peak)
										}
							} else {
								out += RgInfo.TrackGain(it.channels.maxOf { ch -> ch.volumeAdjustment })
								it.channels.maxOfOrNull { ch -> ch.peakVolume ?: 0f }
									?.takeIf { peak -> peak != 0f }?.let { peak ->
									out += RgInfo.TrackPeak(peak)
								}
							}
						} else if (it.identification.startsWith("album", ignoreCase = true)
							|| it.identification.startsWith("audiophile", ignoreCase = true)
							|| it.identification.startsWith("user", ignoreCase = true)) {
							val masterVolume = it.channels.find { ch ->
								ch.channel == Rva2.ChannelEnum.MasterVolume }
							if (masterVolume != null) {
								out += RgInfo.AlbumGain(masterVolume.volumeAdjustment)
								if (masterVolume.peakVolume != null)
									out += RgInfo.AlbumPeak(masterVolume.peakVolume)
								else
									it.channels.maxOf { ch -> ch.peakVolume ?: 0f }
										.takeIf { peak -> peak != 0f }?.let { peak ->
											out += RgInfo.AlbumPeak(peak)
										}
							} else {
								out += RgInfo.AlbumGain(it.channels.maxOf { ch -> ch.volumeAdjustment })
								it.channels.maxOfOrNull { ch -> ch.peakVolume ?: 0f }
									?.takeIf { peak -> peak != 0f }?.let { peak ->
										out += RgInfo.AlbumPeak(peak)
									}
							}
						}
						out
					}
					is Rvad -> {
						val out = mutableListOf<RgInfo>()
						out += RgInfo.TrackGain(it.channels.maxOf { ch -> ch.volumeAdjustment })
						it.channels.maxOf { ch -> ch.peakVolume ?: 0f }
							.takeIf { peak -> peak != 0f }?.let { peak ->
								out += RgInfo.AlbumPeak(peak)
							}
						out
					}
					is SoundCheck -> listOf(RgInfo.TrackGain(max(it.gainL, it.gainR)),
						RgInfo.TrackPeak(max(it.peakL, it.peakR)))
					is R128AlbumGain -> listOf(RgInfo.AlbumGain(it.value + 5))
					is R128TrackGain -> listOf(RgInfo.TrackGain(it.value + 5))
					is TxxxAlbumGain -> listOf(RgInfo.AlbumGain(it.value))
					is TxxxAlbumPeak -> listOf(RgInfo.AlbumPeak(it.value))
					is TxxxTrackGain -> listOf(RgInfo.TrackGain(it.value))
					is TxxxTrackPeak -> listOf(RgInfo.TrackPeak(it.value))
				}
			}
			val trackGain = infos.find { it is RgInfo.TrackGain }?.value
			val trackPeak = infos.find { it is RgInfo.TrackPeak }?.value
			val albumGain = infos.find { it is RgInfo.AlbumGain }?.value
			val albumPeak = infos.find { it is RgInfo.AlbumPeak }?.value
			return ReplayGainInfo(trackGain, trackPeak, albumGain, albumPeak)
		}

		fun calculateGain(tags: ReplayGainInfo?, mode: Mode, rgGain: Int,
		                  reduceGain: Boolean, ratio: Float?): Pair<Float, Float?>? {
			if (ratio == null && !reduceGain) {
				throw IllegalArgumentException("compressor is enabled but no compression ratio")
			}
			val tagGain = when (mode) {
				Mode.Track -> dbToAmpl((tags?.trackGain ?: tags?.albumGain)
					?.plus(rgGain.toFloat()) ?: return null)
				Mode.Album -> dbToAmpl((tags?.albumGain ?: tags?.trackGain)
					?.plus(rgGain.toFloat()) ?: return null)
				Mode.None -> 1f
			}
			val tagPeak = when (mode) {
				Mode.Track -> tags?.trackPeak ?: tags?.albumPeak ?: 1f
				Mode.Album -> tags?.albumPeak ?: tags?.trackPeak ?: 1f
				Mode.None -> 1f
			}
			val gain = if (reduceGain) {
				min(tagGain, if (tagPeak == 0f) 1f else 1f / tagPeak)
			} else {
				tagGain
			}
			val postGainPeakDb = amplToDb(
				(if (tagPeak == 0f) 1f else tagPeak) * (if (gain == 0f) 0.001f else gain))
			if (postGainPeakDb > 0f && reduceGain) {
				throw IllegalStateException("reduceGain true but $postGainPeakDb > 0 (" +
						"$tagPeak * $gain - from $tags)")
			}
			val kneeThresholdDb = if (postGainPeakDb > 0f)
				postGainPeakDb - postGainPeakDb * ratio!! / (ratio - 1f) else null
			return gain to kneeThresholdDb
		}

		// this is copied from ExoPlayer's Id3Decoder but removed fromIndex
		private fun indexOfZeroByte(data: ByteArray): Int {
			for (i in 0 until data.size) {
				if (data[i] == 0.toByte()) {
					return i
				}
			}
			return data.size
		}

		fun amplToDb(ampl: Float): Float {
			if (ampl == 0f) {
				return -758f
			}
			return 20 * log10(ampl)
		}

		fun dbToAmpl(db: Float): Float {
			if (db <= -758f) {
				return 0f
			}
			return exp(db * ln(10f) / 20f)
		}
	}
}