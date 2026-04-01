package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.constant.MessageConstant;
import cn.edu.seig.vibemusic.mapper.PlaylistBindingMapper;
import cn.edu.seig.vibemusic.mapper.PlaylistMapper;
import cn.edu.seig.vibemusic.mapper.SongMapper;
import cn.edu.seig.vibemusic.model.dto.PlaylistSongBindingDTO;
import cn.edu.seig.vibemusic.model.entity.Playlist;
import cn.edu.seig.vibemusic.model.entity.PlaylistBinding;
import cn.edu.seig.vibemusic.model.entity.Song;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IPlaylistBindingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 歌单歌曲绑定服务实现
 * </p>
 *
 * @author Tanh
 * @since 2026-03-22
 */
@Service
@CacheConfig(cacheNames = "playlistCache")
public class PlaylistBindingServiceImpl extends ServiceImpl<PlaylistBindingMapper, PlaylistBinding> implements IPlaylistBindingService {

    @Autowired
    private PlaylistBindingMapper playlistBindingMapper;
    @Autowired
    private PlaylistMapper playlistMapper;
    @Autowired
    private SongMapper songMapper;

    /**
     * 查询歌单已绑定的歌曲 ID
     *
     * @author Tanh
     * @since 2026-03-22
     */
    @Override
    public Result<List<Long>> getPlaylistSongIds(Long playlistId) {
        if (playlistId == null) {
            return Result.error(MessageConstant.PLAYLIST + "ID" + MessageConstant.NOT_NULL);
        }

        Playlist playlist = playlistMapper.selectById(playlistId);
        if (playlist == null) {
            return Result.error(MessageConstant.PLAYLIST + MessageConstant.NOT_FOUND);
        }

        List<Long> songIds = playlistBindingMapper.getSongIdsByPlaylistId(playlistId);
        return Result.success(songIds);
    }

    /**
     * 追加绑定歌曲到歌单（已绑定的会自动跳过）
     *
     * @author Tanh
     * @since 2026-03-22
     */
    @Override
    @CacheEvict(allEntries = true)
    public Result bindSongsToPlaylist(PlaylistSongBindingDTO playlistSongBindingDTO) {
        String errorMessage = validatePlaylistBindingInput(playlistSongBindingDTO, true);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        Long playlistId = playlistSongBindingDTO.getPlaylistId();
        List<Long> uniqueSongIds = normalizeSongIds(playlistSongBindingDTO.getSongIds());

        List<Long> boundSongIds = playlistBindingMapper.getBoundSongIds(playlistId, uniqueSongIds);
        Set<Long> boundSongIdSet = new LinkedHashSet<>(boundSongIds);
        List<Long> toInsertSongIds = uniqueSongIds.stream()
                .filter(songId -> !boundSongIdSet.contains(songId))
                .toList();

        if (CollectionUtils.isEmpty(toInsertSongIds)) {
            return Result.success("歌曲已全部绑定，无需重复操作");
        }

        int inserted = playlistBindingMapper.batchInsert(playlistId, toInsertSongIds);
        if (inserted == 0) {
            return Result.error(MessageConstant.ADD + MessageConstant.FAILED);
        }

        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS + "，新增绑定 " + inserted + " 条");
    }

    /**
     * 用指定歌曲列表覆盖歌单绑定关系
     *
     * @author Tanh
     * @since 2026-03-22
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true)
    public Result replacePlaylistSongs(PlaylistSongBindingDTO playlistSongBindingDTO) {
        String errorMessage = validatePlaylistBindingInput(playlistSongBindingDTO, false);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        Long playlistId = playlistSongBindingDTO.getPlaylistId();
        List<Long> uniqueSongIds = normalizeSongIds(playlistSongBindingDTO.getSongIds());

        playlistBindingMapper.deleteByPlaylistId(playlistId);

        if (CollectionUtils.isEmpty(uniqueSongIds)) {
            return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS + "，歌单已清空");
        }

        int inserted = playlistBindingMapper.batchInsert(playlistId, uniqueSongIds);
        if (inserted == 0) {
            return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        }

        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS + "，当前绑定 " + inserted + " 首歌曲");
    }

    /**
     * 从歌单解绑指定歌曲
     *
     * @author Tanh
     * @since 2026-03-22
     */
    @Override
    @CacheEvict(allEntries = true)
    public Result unbindSongsFromPlaylist(PlaylistSongBindingDTO playlistSongBindingDTO) {
        String errorMessage = validatePlaylistBindingInput(playlistSongBindingDTO, true);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        Long playlistId = playlistSongBindingDTO.getPlaylistId();
        List<Long> uniqueSongIds = normalizeSongIds(playlistSongBindingDTO.getSongIds());

        int deleted = playlistBindingMapper.deleteByPlaylistIdAndSongIds(playlistId, uniqueSongIds);
        if (deleted == 0) {
            return Result.success("未删除任何绑定关系");
        }

        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS + "，已解绑 " + deleted + " 条");
    }

    private String validatePlaylistBindingInput(PlaylistSongBindingDTO playlistSongBindingDTO, boolean requireSongs) {
        if (playlistSongBindingDTO == null) {
            return "请求参数不能为空";
        }

        Long playlistId = playlistSongBindingDTO.getPlaylistId();
        if (playlistId == null) {
            return MessageConstant.PLAYLIST + "ID" + MessageConstant.NOT_NULL;
        }

        Playlist playlist = playlistMapper.selectById(playlistId);
        if (playlist == null) {
            return MessageConstant.PLAYLIST + MessageConstant.NOT_FOUND;
        }

        List<Long> uniqueSongIds = normalizeSongIds(playlistSongBindingDTO.getSongIds());
        if (requireSongs && CollectionUtils.isEmpty(uniqueSongIds)) {
            return MessageConstant.SONG + "ID 列表" + MessageConstant.NOT_NULL;
        }
        if (!CollectionUtils.isEmpty(uniqueSongIds)) {
            List<Song> songs = songMapper.selectByIds(uniqueSongIds);
            Set<Long> existedSongIds = songs.stream()
                    .map(Song::getSongId)
                    .collect(Collectors.toSet());
            List<Long> notFoundSongIds = uniqueSongIds.stream()
                    .filter(songId -> !existedSongIds.contains(songId))
                    .toList();
            if (!CollectionUtils.isEmpty(notFoundSongIds)) {
                return MessageConstant.SONG + MessageConstant.NOT_FOUND + ": " + notFoundSongIds;
            }
        }

        return null;
    }

    private List<Long> normalizeSongIds(List<Long> songIds) {
        if (CollectionUtils.isEmpty(songIds)) {
            return new ArrayList<>();
        }
        return songIds.stream()
                .filter(songId -> songId != null && songId > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }
}
