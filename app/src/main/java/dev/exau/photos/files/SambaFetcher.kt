package dev.exau.photos.files

import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer

class SambaFetcher(
    private val data: SambaImage,
    private val shares: () -> List<SambaShare>,
    private val browser: SambaBrowser,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val share = shares().find { it.id == data.shareId } ?: return@withContext null
        val bytes = browser.readLimited(share, data.path)
        if (bytes.isEmpty()) return@withContext null
        SourceResult(
            source = coil.decode.ImageSource(Buffer().write(bytes), options.context),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(
        private val shares: () -> List<SambaShare>,
        private val browser: SambaBrowser,
    ) : Fetcher.Factory<SambaImage> {
        override fun create(data: SambaImage, options: Options, imageLoader: ImageLoader): Fetcher =
            SambaFetcher(data, shares, browser, options)
    }
}
