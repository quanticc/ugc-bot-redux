package commands

import com.ugcleague.ops.groovy.Repositories
import com.ugcleague.ops.groovy.Services
import org.crsh.cli.Argument
import org.crsh.cli.Command
import org.crsh.cli.Required
import org.crsh.cli.Usage
import org.crsh.command.InvocationContext

import java.util.stream.Collectors

@Usage("[UGC] Discord bot commander")
class discord {

    Services services = new Services()
    Repositories repositories = new Repositories()

    @Usage("connect to server")
    @Command
    def connect(InvocationContext context) {
        services.discord(context).login()
    }

    @Usage("check if bot is ready to interact with the API")
    @Command
    def ready(InvocationContext context) {
        return client(context).isReady()
    }

    @Usage("get bot details")
    @Command
    def info(InvocationContext context) {
        return client(context).getOurUser().toString()
    }

    @Usage("join a server")
    @Command
    def join(
        @Usage("invite URL")
        @Required @Argument
            String inviteIdOrUrl, InvocationContext context) {
        String prefix = "https://discord.gg/"
        if (inviteIdOrUrl.startsWith(prefix)) {
            return client(context).getInviteForCode(inviteIdOrUrl.substring(prefix.length())).accept();
        } else {
            return client(context).getInviteForCode(inviteIdOrUrl).accept();
        }
    }

    @Usage("list of channels")
    @Command
    def channels(InvocationContext context) {
        return client(context).getGuilds().stream().flatMap({ g -> g.getChannels().stream() }).collect(Collectors.toList())
    }

    @Usage("send message to channels")
    @Command
    def send(
        @Usage("channel name regex")
        @Required @Argument
            String channelRegex,
        @Usage("the message to be sent")
        @Required @Argument
            String message, InvocationContext context) {
        return services.discord(context).send(channelRegex, message)
    }

    @Usage("get client instance")
    @Command
    def client(InvocationContext context) {
        return services.discord(context).getClient()
    }

}
