package org.akanework.gramophone.logic.utils.exoplayer

import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.BundledExtractorsAdapter
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ExternalLoader
import androidx.media3.exoplayer.source.ExternallyLoadedMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap.Unseekable
import androidx.media3.extractor.text.SubtitleParser
import com.google.common.base.Preconditions
import com.google.common.base.Supplier
import com.google.common.primitives.Ints
import java.io.IOException

@Suppress("unused")
class GramophoneMediaSourceFactory(
    private var dataSourceFactory: DataSource.Factory,
    extractorsFactory: ExtractorsFactory
) : MediaSource.Factory {
    private val delegateFactoryLoader: DelegateFactoryLoader =
        DelegateFactoryLoader(extractorsFactory, SubtitleParser.Factory.UNSUPPORTED)
    private var serverSideAdInsertionMediaSourceFactory: MediaSource.Factory? = null
    private var externalImageLoader: ExternalLoader? = null
    private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null
    private var liveTargetOffsetMs: Long
    private var liveMinOffsetMs: Long
    private var liveMaxOffsetMs: Long
    private var liveMinSpeed: Float
    private var liveMaxSpeed: Float

    init {
        delegateFactoryLoader.setDataSourceFactory(dataSourceFactory)
        this.liveTargetOffsetMs = -9223372036854775807L
        this.liveMinOffsetMs = -9223372036854775807L
        this.liveMaxOffsetMs = -9223372036854775807L
        this.liveMinSpeed = -3.4028235E38f
        this.liveMaxSpeed = -3.4028235E38f
    }

    fun setExternalImageLoader(externalImageLoader: ExternalLoader?): GramophoneMediaSourceFactory {
        this.externalImageLoader = externalImageLoader
        return this
    }

    fun setLiveTargetOffsetMs(liveTargetOffsetMs: Long): GramophoneMediaSourceFactory {
        this.liveTargetOffsetMs = liveTargetOffsetMs
        return this
    }

    fun setLiveMinOffsetMs(liveMinOffsetMs: Long): GramophoneMediaSourceFactory {
        this.liveMinOffsetMs = liveMinOffsetMs
        return this
    }

    fun setLiveMaxOffsetMs(liveMaxOffsetMs: Long): GramophoneMediaSourceFactory {
        this.liveMaxOffsetMs = liveMaxOffsetMs
        return this
    }

    fun setLiveMinSpeed(minSpeed: Float): GramophoneMediaSourceFactory {
        this.liveMinSpeed = minSpeed
        return this
    }

    fun setLiveMaxSpeed(maxSpeed: Float): GramophoneMediaSourceFactory {
        this.liveMaxSpeed = maxSpeed
        return this
    }

    override fun setCmcdConfigurationFactory(cmcdConfigurationFactory: CmcdConfiguration.Factory): GramophoneMediaSourceFactory {
        delegateFactoryLoader.setCmcdConfigurationFactory(
            Preconditions.checkNotNull(
                cmcdConfigurationFactory
            )
        )
        return this
    }

    override fun setDrmSessionManagerProvider(param: DrmSessionManagerProvider): MediaSource.Factory {
        throw UnsupportedOperationException("drm is not supported")
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): GramophoneMediaSourceFactory {
        this.loadErrorHandlingPolicy =
            Preconditions.checkNotNull(
                loadErrorHandlingPolicy,
                "MediaSource.Factory#setLoadErrorHandlingPolicy no longer handles null by instantiating a new DefaultLoadErrorHandlingPolicy. Explicitly construct and pass an instance in order to retain the old behavior."
            )
        delegateFactoryLoader.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray {
        return delegateFactoryLoader.getSupportedTypes()
    }

    override fun createMediaSource(inMediaItem: MediaItem): MediaSource {
        var mediaItem = inMediaItem
        Preconditions.checkNotNull(mediaItem.localConfiguration)
        val scheme = mediaItem.localConfiguration!!.uri.scheme
        if (scheme != null && (scheme == "ssai")) {
            return Preconditions.checkNotNull(this.serverSideAdInsertionMediaSourceFactory)
                .createMediaSource(mediaItem)
        } else if ((mediaItem.localConfiguration!!.mimeType == "application/x-image-uri")) {
            return (ExternallyLoadedMediaSource.Factory(
                Util.msToUs(
                    mediaItem.localConfiguration!!.imageDurationMs
                ), Preconditions.checkNotNull(
                    this.externalImageLoader
                )
            )).createMediaSource(mediaItem)
        } else {
            val type = Util.inferContentTypeForUriAndMimeType(
                mediaItem.localConfiguration!!.uri, mediaItem.localConfiguration!!.mimeType
            )
            if (mediaItem.localConfiguration!!.imageDurationMs != -9223372036854775807L) {
                delegateFactoryLoader.setJpegExtractorFlags(1)
            }

            val mediaSourceFactory = delegateFactoryLoader.getMediaSourceFactory(type)
            Preconditions.checkNotNull(
                mediaSourceFactory,
                "No suitable media source factory found for content type: $type"
            )
            val liveConfigurationBuilder = mediaItem.liveConfiguration.buildUpon()
            if (mediaItem.liveConfiguration.targetOffsetMs == -9223372036854775807L) {
                liveConfigurationBuilder.setTargetOffsetMs(this.liveTargetOffsetMs)
            }

            if (mediaItem.liveConfiguration.minPlaybackSpeed == -3.4028235E38f) {
                liveConfigurationBuilder.setMinPlaybackSpeed(this.liveMinSpeed)
            }

            if (mediaItem.liveConfiguration.maxPlaybackSpeed == -3.4028235E38f) {
                liveConfigurationBuilder.setMaxPlaybackSpeed(this.liveMaxSpeed)
            }

            if (mediaItem.liveConfiguration.minOffsetMs == -9223372036854775807L) {
                liveConfigurationBuilder.setMinOffsetMs(this.liveMinOffsetMs)
            }

            if (mediaItem.liveConfiguration.maxOffsetMs == -9223372036854775807L) {
                liveConfigurationBuilder.setMaxOffsetMs(this.liveMaxOffsetMs)
            }

            val liveConfiguration = liveConfigurationBuilder.build()
            if (liveConfiguration != mediaItem.liveConfiguration) {
                mediaItem = mediaItem.buildUpon().setLiveConfiguration(liveConfiguration).build()
            }

            var mediaSource: MediaSource = mediaSourceFactory!!.createMediaSource(mediaItem)
            val subtitleConfigurations: List<SubtitleConfiguration> =
                Util.castNonNull(mediaItem.localConfiguration).subtitleConfigurations
            if (subtitleConfigurations.isNotEmpty()) {
                val mediaSources = ArrayList<MediaSource>(subtitleConfigurations.size + 1)
                mediaSources[0] = mediaSource

                for (i in subtitleConfigurations.indices) {
                    val format = (Format.Builder()).setSampleMimeType(
                        subtitleConfigurations[i].mimeType
                    ).setLanguage(
                        subtitleConfigurations[i].language
                    ).setSelectionFlags(
                        subtitleConfigurations[i].selectionFlags
                    ).setRoleFlags(
                        subtitleConfigurations[i].roleFlags
                    ).setLabel(
                        subtitleConfigurations[i].label
                    ).setId(subtitleConfigurations[i].id).build()
                    val extractorsFactory =
                        ExtractorsFactory { arrayOf(UnknownSubtitlesExtractor(format)) }
                    val progressiveMediaSourceFactory = ProgressiveMediaSource.Factory(
                        this.dataSourceFactory,
                        { BundledExtractorsAdapter(extractorsFactory) },
                        { DrmSessionManager.DRM_UNSUPPORTED },
                        DefaultLoadErrorHandlingPolicy(),
                        1048576
                    )
                    if (this.loadErrorHandlingPolicy != null) {
                        progressiveMediaSourceFactory.setLoadErrorHandlingPolicy(
                            loadErrorHandlingPolicy!!
                        )
                    }

                    mediaSources[i + 1] = progressiveMediaSourceFactory.createMediaSource(
                        MediaItem.fromUri(
                            subtitleConfigurations[i].uri.toString()
                        )
                    )
                }

                mediaSource = MergingMediaSource(*mediaSources.toTypedArray())
            }

            return maybeClipMediaSource(
                mediaItem,
                mediaSource
            )
        }
    }

    private class DelegateFactoryLoader(
        private val extractorsFactory: ExtractorsFactory,
        private var subtitleParserFactory: SubtitleParser.Factory
    ) {
        private val mediaSourceFactorySuppliers: MutableMap<Int?, Supplier<MediaSource.Factory>?> =
            hashMapOf()
        private val supportedTypes: MutableSet<Int?> = hashSetOf()
        private val mediaSourceFactories: MutableMap<Int?, MediaSource.Factory?> = hashMapOf()
        private var dataSourceFactory: DataSource.Factory? = null
        private var cmcdConfigurationFactory: CmcdConfiguration.Factory? = null
        private var drmSessionManagerProvider: DrmSessionManagerProvider? = null
        private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null

        fun getSupportedTypes(): IntArray {
            this.ensureAllSuppliersAreLoaded()
            return Ints.toArray(this.supportedTypes)
        }

        fun getMediaSourceFactory(contentType: Int): MediaSource.Factory? {
            var mediaSourceFactory = mediaSourceFactories[contentType]
            if (mediaSourceFactory != null) {
                return mediaSourceFactory
            } else {
                val mediaSourceFactorySupplier = this.maybeLoadSupplier(contentType)
                if (mediaSourceFactorySupplier == null) {
                    return null
                } else {
                    mediaSourceFactory = mediaSourceFactorySupplier.get() as MediaSource.Factory
                    if (this.cmcdConfigurationFactory != null) {
                        mediaSourceFactory.setCmcdConfigurationFactory(
                            cmcdConfigurationFactory!!
                        )
                    }

                    if (this.drmSessionManagerProvider != null) {
                        mediaSourceFactory.setDrmSessionManagerProvider(
                            drmSessionManagerProvider!!
                        )
                    }

                    if (this.loadErrorHandlingPolicy != null) {
                        mediaSourceFactory.setLoadErrorHandlingPolicy(
                            loadErrorHandlingPolicy!!
                        )
                    }

                    mediaSourceFactory.setSubtitleParserFactory(this.subtitleParserFactory)
                    mediaSourceFactories[contentType] = mediaSourceFactory
                    return mediaSourceFactory
                }
            }
        }

        fun setDataSourceFactory(dataSourceFactory: DataSource.Factory) {
            if (dataSourceFactory !== this.dataSourceFactory) {
                this.dataSourceFactory = dataSourceFactory
                mediaSourceFactorySuppliers.clear()
                mediaSourceFactories.clear()
            }
        }

        fun setCmcdConfigurationFactory(cmcdConfigurationFactory: CmcdConfiguration.Factory?) {
            this.cmcdConfigurationFactory = cmcdConfigurationFactory
            val var2: Iterator<*> = mediaSourceFactories.values.iterator()

            while (var2.hasNext()) {
                val mediaSourceFactory = var2.next() as MediaSource.Factory
                mediaSourceFactory.setCmcdConfigurationFactory((cmcdConfigurationFactory)!!)
            }
        }

        fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy?) {
            this.loadErrorHandlingPolicy = loadErrorHandlingPolicy
            val var2: Iterator<*> = mediaSourceFactories.values.iterator()

            while (var2.hasNext()) {
                val mediaSourceFactory = var2.next() as MediaSource.Factory
                mediaSourceFactory.setLoadErrorHandlingPolicy((loadErrorHandlingPolicy)!!)
            }
        }

        fun setJpegExtractorFlags(flags: Int) {
            if (extractorsFactory is DefaultExtractorsFactory) {
                extractorsFactory.setJpegExtractorFlags(flags)
            }
        }

        private fun ensureAllSuppliersAreLoaded() {
            this.maybeLoadSupplier(0)
            this.maybeLoadSupplier(1)
            this.maybeLoadSupplier(2)
            this.maybeLoadSupplier(3)
            this.maybeLoadSupplier(4)
        }

        private fun maybeLoadSupplier(contentType: Int): Supplier<MediaSource.Factory>? {
            if (mediaSourceFactorySuppliers.containsKey(contentType)) {
                return mediaSourceFactorySuppliers[contentType]
            } else {
                var mediaSourceFactorySupplier: Supplier<MediaSource.Factory>? = null
                val dataSourceFactory = Preconditions.checkNotNull<DataSource.Factory>(
                    this.dataSourceFactory
                )

                try {
                    val clazz: Class<*>
                    when (contentType) {
                        0 -> {
                            clazz =
                                Class.forName($$"androidx.media3.exoplayer.dash.DashMediaSource$Factory")
                                    .asSubclass(
                                        MediaSource.Factory::class.java
                                    )
                            mediaSourceFactorySupplier = Supplier {
                                newInstance(
                                    clazz,
                                    dataSourceFactory
                                )
                            }
                        }

                        1 -> {
                            clazz =
                                Class.forName($$"androidx.media3.exoplayer.smoothstreaming.SsMediaSource$Factory")
                                    .asSubclass(
                                        MediaSource.Factory::class.java
                                    )
                            mediaSourceFactorySupplier = Supplier {
                                newInstance(
                                    clazz,
                                    dataSourceFactory
                                )
                            }
                        }

                        2 -> {
                            clazz =
                                Class.forName($$"androidx.media3.exoplayer.hls.HlsMediaSource$Factory")
                                    .asSubclass(
                                        MediaSource.Factory::class.java
                                    )
                            mediaSourceFactorySupplier = Supplier {
                                newInstance(
                                    clazz,
                                    dataSourceFactory
                                )
                            }
                        }

                        3 -> {
                            clazz =
                                Class.forName($$"androidx.media3.exoplayer.rtsp.RtspMediaSource$Factory")
                                    .asSubclass(
                                        MediaSource.Factory::class.java
                                    )
                            mediaSourceFactorySupplier = Supplier {
                                newInstance(
                                    clazz
                                )
                            }
                        }

                        4 -> mediaSourceFactorySupplier =
                            Supplier {
                                ProgressiveMediaSource.Factory(
                                    dataSourceFactory,
                                    { BundledExtractorsAdapter(extractorsFactory) },
                                    { DrmSessionManager.DRM_UNSUPPORTED },
                                    DefaultLoadErrorHandlingPolicy(),
                                    1048576
                                )
                            }
                    }
                } catch (_: ClassNotFoundException) {
                }

                mediaSourceFactorySuppliers[contentType] =
                    mediaSourceFactorySupplier
                if (mediaSourceFactorySupplier != null) {
                    supportedTypes.add(contentType)
                }

                return mediaSourceFactorySupplier
            }
        }
    }

    private class UnknownSubtitlesExtractor(private val format: Format) : Extractor {
        override fun sniff(input: ExtractorInput): Boolean {
            return true
        }

        override fun init(output: ExtractorOutput) {
            val trackOutput = output.track(0, 3)
            output.seekMap(Unseekable(-9223372036854775807L))
            output.endTracks()
            trackOutput.format(
                format.buildUpon().setSampleMimeType("text/x-unknown").setCodecs(
                    format.sampleMimeType
                ).build()
            )
        }

        @Throws(IOException::class)
        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
            val skipResult = input.skip(Int.MAX_VALUE)
            return if (skipResult == -1) Extractor.RESULT_END_OF_INPUT else Extractor.RESULT_CONTINUE
        }

        override fun seek(position: Long, timeUs: Long) {
        }

        override fun release() {
        }
    }

    companion object {
        private fun maybeClipMediaSource(
            mediaItem: MediaItem,
            mediaSource: MediaSource
        ): MediaSource {
            return (if ((mediaItem.clippingConfiguration.startPositionUs == 0L) && (mediaItem.clippingConfiguration.endPositionUs == Long.MIN_VALUE) && !mediaItem.clippingConfiguration.relativeToDefaultPosition)
                mediaSource
            else
                ClippingMediaSource.Builder(mediaSource)
                    .setStartPositionUs(mediaItem.clippingConfiguration.startPositionUs)
                    .setEndPositionUs(mediaItem.clippingConfiguration.endPositionUs)
                    .setEnableInitialDiscontinuity(!mediaItem.clippingConfiguration.startsAtKeyFrame)
                    .setAllowDynamicClippingUpdates(mediaItem.clippingConfiguration.relativeToLiveWindow)
                    .setRelativeToDefaultPosition(mediaItem.clippingConfiguration.relativeToDefaultPosition)
                    .build())
        }

        private fun newInstance(
            clazz: Class<out MediaSource.Factory>,
            dataSourceFactory: DataSource.Factory
        ): MediaSource.Factory {
            try {
                return clazz.getConstructor(DataSource.Factory::class.java)
                    .newInstance(dataSourceFactory) as MediaSource.Factory
            } catch (var3: Exception) {
                throw IllegalStateException(var3)
            }
        }

        private fun newInstance(clazz: Class<out MediaSource.Factory>): MediaSource.Factory {
            try {
                return clazz.getConstructor().newInstance() as MediaSource.Factory
            } catch (var2: Exception) {
                throw IllegalStateException(var2)
            }
        }
    }
}
