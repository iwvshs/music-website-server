package cn.edu.seig.vibemusic.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 歌单歌曲绑定参数
 *
 * @author Tanh
 * @since 2026-03-22
 */
@Data
public class PlaylistSongBindingDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 歌单 ID
     */
    private Long playlistId;

    /**
     * 歌曲 ID 列表
     */
    private List<Long> songIds;
}
