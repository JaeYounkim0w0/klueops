package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ClusterDataDeletionPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcClusterDataDeletionAdapter implements ClusterDataDeletionPort {

    private final JdbcTemplate jdbcTemplate;

    /** JdbcClusterDataDeletionAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JdbcClusterDataDeletionAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** JdbcClusterDataDeletionAdapter의 deleteClusterData 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteClusterData(UUID clusterId) {
        List<UUID> asyncJobIds = jdbcTemplate.queryForList("""
                select async_job_id
                from sync_jobs
                where cluster_id = ?
                """, UUID.class, clusterId);

        jdbcTemplate.update("""
                delete from ai_chat_context_references
                where message_id in (
                    select m.id
                    from ai_chat_messages m
                    join ai_chat_conversations c on c.id = m.conversation_id
                    where c.cluster_id = ?
                )
                """, clusterId);
        jdbcTemplate.update("""
                delete from ai_chat_messages
                where conversation_id in (
                    select id from ai_chat_conversations where cluster_id = ?
                )
                """, clusterId);
        jdbcTemplate.update("delete from ai_chat_conversations where cluster_id = ?", clusterId);
        jdbcTemplate.update("delete from analysis_sessions where cluster_id = ?", clusterId);
        jdbcTemplate.update("delete from managed_applications where cluster_id = ?", clusterId);
        jdbcTemplate.update("delete from kubernetes_event_snapshots where cluster_id = ?", clusterId);
        jdbcTemplate.update("delete from kubernetes_resource_snapshots where cluster_id = ?", clusterId);
        jdbcTemplate.update("delete from sync_jobs where cluster_id = ?", clusterId);
        asyncJobIds.forEach(asyncJobId -> jdbcTemplate.update("delete from async_jobs where id = ?", asyncJobId));
        jdbcTemplate.update("delete from cluster_sync_settings where cluster_id = ?", clusterId);
        jdbcTemplate.update("delete from cluster_credentials where cluster_id = ?", clusterId);
        jdbcTemplate.update("delete from clusters where id = ?", clusterId);
    }
}
