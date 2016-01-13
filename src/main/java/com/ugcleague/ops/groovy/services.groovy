package com.ugcleague.ops.groovy

import com.ugcleague.ops.service.DiscordService
import com.ugcleague.ops.service.GameServerService
import org.crsh.command.InvocationContext
import org.springframework.beans.factory.BeanFactory

/**
 * Helper class to access Spring services
 */
class Services {

    BeanFactory beanFactory(InvocationContext context) {
        return context.attributes['spring.beanfactory'] as BeanFactory
    }

    GameServerService gameServer(InvocationContext context) {
        return beanFactory(context).getBean(GameServerService.class)
    }

    DiscordService discord(InvocationContext context) {
        return beanFactory(context).getBean(DiscordService.class)
    }
}
