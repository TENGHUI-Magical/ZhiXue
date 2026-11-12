package com.zhixue.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 定时任务线程池（P1 修复 · Java_10）。
 *
 * 问题：ServerApplication 的 @EnableScheduling 使用 Spring 默认单线程池，而当前有 3 个定时任务
 * （OcrService 回收器 / OcrService 图片清理 / PortraitService 每日画像快照），任一任务变慢或抛异常
 * 会阻塞其余任务，且默认 ErrorHandler 只打一条日志、异常后该任务可能不再调度。
 *
 * 修复：显式 2 线程池 + 统一 ErrorHandler + 线程名前缀，便于在日志里区分定时线程。
 * 不引入任何新依赖，符合模块化单体架构约束。
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    @Bean(destroyMethod = "shutdown")
    public TaskScheduler zhixueTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("zhixue-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.setErrorHandler(throwable ->
                log.error("定时任务执行失败（不影响其它任务调度）", throwable));
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(zhixueTaskScheduler());
    }
}
