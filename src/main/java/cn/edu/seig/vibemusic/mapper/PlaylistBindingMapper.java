package cn.edu.seig.vibemusic.mapper;

import cn.edu.seig.vibemusic.model.entity.PlaylistBinding;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 歌单歌曲绑定 Mapper
 * </p>
 *
 * @author Tanh
 * @since 2026-03-22
 */
@Mapper
public interface PlaylistBindingMapper extends BaseMapper<PlaylistBinding> {

    List<Long> getSongIdsByPlaylistId(@Param("playlistId") Long playlistId);

    List<Long> getBoundSongIds(@Param("playlistId") Long playlistId, @Param("songIds") List<Long> songIds);

    int batchInsert(@Param("playlistId") Long playlistId, @Param("songIds") List<Long> songIds);

    int deleteByPlaylistId(@Param("playlistId") Long playlistId);

    int deleteByPlaylistIdAndSongIds(@Param("playlistId") Long playlistId, @Param("songIds") List<Long> songIds);
}
