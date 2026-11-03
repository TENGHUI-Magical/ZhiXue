package com.zhixue.user.service;

import com.zhixue.user.entity.SysUser;
import com.zhixue.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户只读查询：供其他模块批量补 userName，不暴露 sys_user 表。
 */
@Service
public class UserQueryService {

    private final SysUserMapper sysUserMapper;

    public UserQueryService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    private static final int BATCH_SIZE = 200;

    /**
     * 批量取展示名：realName 为空时回退 username。
     * 不存在的 userId 不放入结果，调用方自行兜底。
     */
    public Map<Long, String> findDisplayNames(Collection<Long> userIds) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (SysUser user : findUsers(userIds)) {
            String name = user.getRealName();
            if (name == null || name.isBlank()) {
                name = user.getUsername();
            }
            result.put(user.getUserId(), name);
        }
        return result;
    }

    /** 批量取登录用户名/演示 ID；每批最多 200 个 ID，供教师导出 studentKey。 */
    public Map<Long, String> findUsernames(Collection<Long> userIds) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (SysUser user : findUsers(userIds)) {
            result.put(user.getUserId(), user.getUsername());
        }
        return result;
    }

    private List<SysUser> findUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<SysUser> result = new java.util.ArrayList<>();
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            result.addAll(sysUserMapper.selectBatchIds(
                    ids.subList(from, Math.min(ids.size(), from + BATCH_SIZE))));
        }
        return result;
    }
}
