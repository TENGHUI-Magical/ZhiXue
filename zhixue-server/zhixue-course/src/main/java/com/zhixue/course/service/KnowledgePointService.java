package com.zhixue.course.service;

import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.mapper.KnowledgePointMapper;
import org.springframework.stereotype.Service;

/**
 * 知识点服务：完整分层切片示例的 service 层。
 */
@Service
public class KnowledgePointService {

    private final KnowledgePointMapper knowledgePointMapper;

    public KnowledgePointService(KnowledgePointMapper knowledgePointMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
    }

    /**
     * 按业务编号查询知识点（接口契约文档 3 节附录示例）。
     *
     * @param kpId 知识点编号，如 KP0101
     * @return 知识点；不存在抛 40400
     */
    public KnowledgePoint getById(String kpId) {
        KnowledgePoint kp = knowledgePointMapper.selectById(kpId);
        if (kp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识点不存在: " + kpId);
        }
        return kp;
    }
}