package commands

import com.ugcleague.ops.repository.GameServerRepository
import org.crsh.cli.Command
import org.crsh.cli.Usage
import org.crsh.command.InvocationContext
import org.springframework.beans.factory.BeanFactory

@Usage("[UGC] Server operator")
class Server {

    @Usage("list all servers")
    @Command
    def list(InvocationContext context) {
        return repository(context).findAll()
    }

    // utilities

    BeanFactory beanFactory(InvocationContext context) {
        return context.attributes['spring.beanfactory'] as BeanFactory
    }

    GameServerRepository repository(InvocationContext context) {
        return beanFactory(context).getBean(GameServerRepository.class)
    }
}


