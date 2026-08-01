package com.soundsphere.innertube.pages

import com.soundsphere.innertube.models.Album
import com.soundsphere.innertube.models.AlbumItem
import com.soundsphere.innertube.models.Artist
import com.soundsphere.innertube.models.ArtistItem
import com.soundsphere.innertube.models.MusicResponsiveListItemRenderer
import com.soundsphere.innertube.models.MusicTwoRowItemRenderer
import com.soundsphere.innertube.models.PlaylistItem
import com.soundsphere.innertube.models.SongItem
import com.soundsphere.innertube.models.YTItem
import com.soundsphere.innertube.models.oddElements
import com.soundsphere.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
