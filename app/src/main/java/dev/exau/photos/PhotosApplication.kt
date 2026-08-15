package dev.exau.photos

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.exau.photos.files.SambaBrowser
import dev.exau.photos.files.SambaFetcher
import dev.exau.photos.files.SambaPrefs
import dev.exau.photos.lock.AppLock

class PhotosApplication : Application(), ImageLoaderFactory {
    val sambaBrowser = SambaBrowser()
    val sambaPrefs by lazy { SambaPrefs(this) }
    val appLock by lazy { AppLock(this) }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(false)
            .allowHardware(true)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.28).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
                add(SambaFetcher.Factory({ sambaPrefs.shares() }, sambaBrowser))
            }
            .build()
}
