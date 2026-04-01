package cn.edu.seig.vibemusic.service;

import cn.edu.seig.vibemusic.model.dto.PlaylistSongBindingDTO;
import cn.edu.seig.vibemusic.model.entity.PlaylistBinding;
import cn.edu.seig.vibemusic.result.Result;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 歌单歌曲绑定服务
 * </p>
 *
 * @author Tanh
 * @since 2026-03-22
 */
public interface IPlaylistBindingService extends IService<PlaylistBinding> {

    Result<List<Long>> getPlaylistSongIds(Long playlistId);

    Result bindSongsToPlaylist(PlaylistSongBindingDTO playlistSongBindingDTO);

    Result replacePlaylistSongs(PlaylistSongBindingDTO playlistSongBindingDTO);

    Result unbindSongsFromPlaylist(PlaylistSongBindingDTO playlistSongBindingDTO);
}
