package eu.kanade.tachiyomi.data.source.online

import android.content.Context
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.network.NetworkHelper
import eu.kanade.tachiyomi.data.network.get
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.source.Language
import eu.kanade.tachiyomi.data.source.Source
import eu.kanade.tachiyomi.data.source.model.MangasPage
import eu.kanade.tachiyomi.data.source.model.Page
import okhttp3.*
import rx.Observable
import javax.inject.Inject

/**
 * A simple implementation for sources from a website.
 *
 * @param context the application context.
 */
abstract class OnlineSource(context: Context) : Source {

    /**
     * Network service.
     */
    @Inject lateinit var network: NetworkHelper

    /**
     * Chapter cache.
     */
    @Inject lateinit var chapterCache: ChapterCache

    /**
     * Preferences helper.
     */
    @Inject lateinit var preferences: PreferencesHelper

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    abstract val baseUrl: String

    /**
     * Language of the source.
     */
    abstract val lang: Language

    /**
     * Headers used for requests.
     */
    val headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    open val client: OkHttpClient
        get() = network.defaultClient

    init {
        // Inject dependencies.
        App.get(context).component.inject(this)
    }

    /**
     * Headers builder for requests. Implementations can override this method for custom headers.
     */
    open protected fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
    }

    /**
     * Visible name of the source.
     */
    override fun toString() = "$name (${lang.code})"

    // Login source

    open fun isLoginRequired() = false

    open fun isLogged(): Boolean = throw Exception("Not implemented")

    open fun login(username: String, password: String): Observable<Boolean>
            = throw Exception("Not implemented")

    open fun isAuthenticationSuccessful(response: Response): Boolean
            = throw Exception("Not implemented")

    /**
     * Returns an observable containing a page with a list of manga. Normally it's not needed to
     * override this method.
     *
     * @param page the page object where the information will be saved, like the list of manga,
     *             the current page and the next page url.
     */
    open fun fetchPopularManga(page: MangasPage): Observable<MangasPage> = network
            .request(popularMangaRequest(page), client)
            .map { response ->
                page.apply {
                    mangas = mutableListOf<Manga>()
                    popularMangaParse(response, this)
                }
            }

    /**
     * Returns the request for the popular manga given the page. Override only if it's needed to
     * send different headers or request method like POST.
     *
     * @param page the page object.
     */
    open protected fun popularMangaRequest(page: MangasPage): Request {
        if (page.page == 1) {
            page.url = popularMangaInitialUrl()
        }
        return get(page.url, headers)
    }

    /**
     * Returns the absolute url of the first page to popular manga.
     */
    abstract protected fun popularMangaInitialUrl(): String

    /**
     * Parse the response from the site. It should add a list of manga and the absolute url to the
     * next page (if it has a next one) to [page].
     *
     * @param response the response from the site.
     * @param page the page object to be filled.
     */
    abstract protected fun popularMangaParse(response: Response, page: MangasPage)

    /**
     * Returns an observable containing a page with a list of manga. Normally it's not needed to
     * override this method.
     *
     * @param page the page object where the information will be saved, like the list of manga,
     *             the current page and the next page url.
     * @param query the search query.
     */
    open fun fetchSearchManga(page: MangasPage, query: String): Observable<MangasPage> = network
            .request(searchMangaRequest(page, query), client)
            .map { response ->
                page.apply {
                    mangas = mutableListOf<Manga>()
                    searchMangaParse(response, this, query)
                }
            }

    /**
     * Returns the request for the search manga given the page. Override only if it's needed to
     * send different headers or request method like POST.
     *
     * @param page the page object.
     * @param query the search query.
     */
    open protected fun searchMangaRequest(page: MangasPage, query: String): Request {
        if (page.page == 1) {
            page.url = searchMangaInitialUrl(query)
        }
        return get(page.url, headers)
    }

    /**
     * Returns the absolute url of the first page to popular manga.
     *
     * @param query the search query.
     */
    abstract protected fun searchMangaInitialUrl(query: String): String

    /**
     * Parse the response from the site. It should add a list of manga and the absolute url to the
     * next page (if it has a next one) to [page].
     *
     * @param response the response from the site.
     * @param page the page object to be filled.
     * @param query the search query.
     */
    abstract protected fun searchMangaParse(response: Response, page: MangasPage, query: String)

    /**
     * Returns an observable with the updated details for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to be updated.
     */
    override fun fetchMangaDetails(manga: Manga): Observable<Manga> = network
            .request(mangaDetailsRequest(manga), client)
            .map { response ->
                Manga.create(manga.url, id).apply {
                    mangaDetailsParse(response, this)
                    initialized = true
                }
            }

    /**
     * Returns the request for updating a manga. Override only if it's needed to override the url,
     * send different headers or request method like POST.
     *
     * @param manga the manga to be updated.
     */
    open protected fun mangaDetailsRequest(manga: Manga): Request {
        return get(baseUrl + manga.url, headers)
    }

    /**
     * Parse the response from the site. It should fill [manga].
     *
     * @param response the response from the site.
     * @param manga the manga whose fields have to be filled.
     */
    abstract protected fun mangaDetailsParse(response: Response, manga: Manga)

    /**
     * Returns an observable with the updated chapter list for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to look for chapters.
     */
    override fun fetchChapterList(manga: Manga): Observable<List<Chapter>> = network
            .request(chapterListRequest(manga), client)
            .map { response ->
                mutableListOf<Chapter>().apply {
                    chapterListParse(response, this)
                    if (isEmpty()) {
                        throw Exception("No chapters found")
                    }
                }
            }

    /**
     * Returns the request for updating the chapter list. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param manga the manga to look for chapters.
     */
    open protected fun chapterListRequest(manga: Manga): Request {
        return get(baseUrl + manga.url, headers)
    }

    /**
     * Parse the response from the site. It should fill [chapters].
     *
     * @param response the response from the site.
     * @param chapters the chapter list to be filled.
     */
    abstract protected fun chapterListParse(response: Response, chapters: MutableList<Chapter>)

    /**
     * Returns an observable with the page list for a chapter. It tries to return the page list from
     * the local cache, otherwise fallbacks to network calling [fetchPageListFromNetwork].
     *
     * @param chapter the chapter whose page list has to be fetched.
     */
    final override fun fetchPageList(chapter: Chapter): Observable<List<Page>> = chapterCache
            .getPageListFromCache(getChapterCacheKey(chapter))
            .onErrorResumeNext { fetchPageListFromNetwork(chapter) }

    /**
     * Returns an observable with the page list for a chapter. Normally it's not needed to override
     * this method.
     *
     * @param chapter the chapter whose page list has to be fetched.
     */
    open fun fetchPageListFromNetwork(chapter: Chapter): Observable<List<Page>> = network
            .request(pageListRequest(chapter), client)
            .map { response ->
                mutableListOf<Page>().apply {
                    pageListParse(response, this)
                    if (isEmpty()) {
                        throw Exception("Page list is empty")
                    }
                }
            }

    /**
     * Returns the request for getting the page list. Override only if it's needed to override the
     * url, send different headers or request method like POST.
     *
     * @param chapter the chapter whose page list has to be fetched
     */
    open protected fun pageListRequest(chapter: Chapter): Request {
        return get(baseUrl + chapter.url, headers)
    }

    /**
     * Parse the response from the site. It should fill [pages].
     *
     * @param response the response from the site.
     * @param pages the page list to be filled.
     */
    abstract protected fun pageListParse(response: Response, pages: MutableList<Page>)

    /**
     * Returns the key for the page list to be stored in [ChapterCache].
     */
    private fun getChapterCacheKey(chapter: Chapter) = "$id${chapter.url}"

    /**
     * Returns an observable with the page containing the source url of the image. If there's any
     * error, it will return null instead of throwing an exception.
     *
     * @param page the page whose source image has to be fetched.
     */
    open protected fun fetchImageUrl(page: Page): Observable<Page> {
        page.status = Page.LOAD_PAGE
        return network
                .request(imageUrlRequest(page), client)
                .map { imageUrlParse(it) }
                .doOnError { page.status = Page.ERROR }
                .onErrorReturn { null }
                .doOnNext { page.imageUrl = it }
                .map { page }
    }

    /**
     * Returns the request for getting the url to the source image. Override only if it's needed to
     * override the url, send different headers or request method like POST.
     *
     * @param page the chapter whose page list has to be fetched
     */
    open protected fun imageUrlRequest(page: Page): Request {
        return get(page.url, headers)
    }

    /**
     * Parse the response from the site. It should return the absolute url to the source image.
     *
     * @param response the response from the site.
     */
    abstract protected fun imageUrlParse(response: Response): String

    /**
     * Returns an observable of the page with the downloaded image.
     *
     * @param page the page whose source image has to be downloaded.
     */
    final override fun fetchImage(page: Page): Observable<Page> =
        if (page.imageUrl.isNullOrEmpty())
            fetchImageUrl(page).flatMap { getCachedImage(it) }
        else
            getCachedImage(page)

    /**
     * Returns an observable with the response of the source image.
     *
     * @param page the page whose source image has to be downloaded.
     */
    fun imageResponse(page: Page): Observable<Response> = network
            .requestBodyProgress(imageRequest(page), page)
            .doOnNext {
                if (!it.isSuccessful) {
                    it.body().close()
                    throw RuntimeException("Not a valid response")
                }
            }

    /**
     * Returns the request for getting the source image. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param page the chapter whose page list has to be fetched
     */
    open protected fun imageRequest(page: Page): Request {
        return get(page.imageUrl, headers)
    }

    /**
     * Returns an observable of the page that gets the image from the chapter or fallbacks to
     * network and copies it to the cache calling [cacheImage].
     *
     * @param page the page.
     */
    fun getCachedImage(page: Page): Observable<Page> {
        val pageObservable = Observable.just(page)
        if (page.imageUrl.isNullOrEmpty())
            return pageObservable

        return pageObservable
                .flatMap {
                    if (!chapterCache.isImageInCache(page.imageUrl)) {
                        cacheImage(page)
                    } else {
                        Observable.just(page)
                    }
                }
                .doOnNext {
                    page.imagePath = chapterCache.getImagePath(page.imageUrl)
                    page.status = Page.READY
                }
                .doOnError { page.status = Page.ERROR }
                .onErrorReturn { page }
    }

    /**
     * Returns an observable of the page that downloads the image to [ChapterCache].
     *
     * @param page the page.
     */
    private fun cacheImage(page: Page): Observable<Page> {
        page.status = Page.DOWNLOAD_IMAGE
        return imageResponse(page)
                .doOnNext { chapterCache.putImageToCache(page.imageUrl, it, preferences.reencodeImage()) }
                .map { page }
    }


    // Utility methods

    /**
     * Returns an absolute url from a href.
     *
     * Ex:
     * href="http://example.com/foo" url="http://example.com" -> http://example.com/foo
     * href="/mypath" url="http://example.com/foo" -> http://example.com/mypath
     * href="bar" url="http://example.com/foo" -> http://example.com/bar
     * href="bar" url="http://example.com/foo/" -> http://example.com/foo/bar
     *
     * @param href the href attribute from the html.
     * @param url the requested url.
     */
    fun getAbsoluteUrl(href: String, url: HttpUrl) = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("/") -> url.newBuilder().encodedPath("/").fragment(null).query(null)
                .toString() + href.substring(1)
        else -> url.toString().substringBeforeLast('/') + "/$href"
    }

    fun fetchAllImageUrlsFromPageList(pages: List<Page>) = Observable.from(pages)
            .filter { !it.imageUrl.isNullOrEmpty() }
            .mergeWith(fetchRemainingImageUrlsFromPageList(pages))

    fun fetchRemainingImageUrlsFromPageList(pages: List<Page>) = Observable.from(pages)
            .filter { it.imageUrl.isNullOrEmpty() }
            .concatMap { fetchImageUrl(it) }

    fun savePageList(chapter: Chapter, pages: List<Page>?) {
        if (pages != null) {
            chapterCache.putPageListToCache(getChapterCacheKey(chapter), pages)
        }
    }

    // Overridable method to allow custom parsing.
    open fun parseChapterNumber(chapter: Chapter) {

    }

}