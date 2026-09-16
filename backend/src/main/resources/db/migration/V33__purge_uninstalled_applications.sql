-- 이전 버전에서 삭제 완료 상태로 남은 Application 수명주기 데이터만 정리한다.
delete from application_endpoints
where application_id in (select id from managed_applications where status = 'UNINSTALLED');

delete from application_releases
where application_id in (select id from managed_applications where status = 'UNINSTALLED');

delete from release_operations
where application_id in (select id from managed_applications where status = 'UNINSTALLED');

delete from deployment_plans
where application_id in (select id from managed_applications where status = 'UNINSTALLED');

delete from managed_applications
where status = 'UNINSTALLED';
