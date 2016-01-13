package commands

import com.ugcleague.ops.groovy.Repositories
import com.ugcleague.ops.groovy.Services
import org.crsh.cli.Command
import org.crsh.cli.Usage
import org.crsh.command.InvocationContext

@Usage("[UGC] Server operator")
class Server {

    Services services = new Services()
    Repositories repositories = new Repositories()

    @Usage("list all servers")
    @Command
    def list(InvocationContext context) {
        return repositories.gameServer(context).findAll()
    }

}


