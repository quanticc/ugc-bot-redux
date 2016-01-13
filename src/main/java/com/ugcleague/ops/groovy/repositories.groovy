package com.ugcleague.ops.groovy

import com.ugcleague.ops.repository.FlagRepository
import com.ugcleague.ops.repository.GameServerRepository
import com.ugcleague.ops.repository.ServerFileRepository
import com.ugcleague.ops.repository.SyncGroupRepository
import com.ugcleague.ops.repository.TaskRepository
import org.crsh.command.InvocationContext
import org.springframework.beans.factory.BeanFactory

/**
 * Helper class to access Spring Data repositories
 */
class Repositories {

    BeanFactory beanFactory(InvocationContext context) {
        return context.attributes['spring.beanfactory'] as BeanFactory
    }

    GameServerRepository gameServer(InvocationContext context) {
        return beanFactory(context).getBean(GameServerRepository.class)
    }

    TaskRepository task(InvocationContext context) {
        return beanFactory(context).getBean(TaskRepository.class)
    }

    FlagRepository flag(InvocationContext context) {
        return beanFactory(context).getBean(FlagRepository.class)
    }

    ServerFileRepository serverFile(InvocationContext context) {
        return beanFactory(context).getBean(ServerFileRepository.class)
    }

    SyncGroupRepository syncGroup(InvocationContext context) {
        return beanFactory(context).getBean(SyncGroupRepository.class)
    }
}
