package commands

import com.ugcleague.ops.domain.Task
import com.ugcleague.ops.groovy.Repositories
import com.ugcleague.ops.groovy.Services
import com.ugcleague.ops.repository.TaskRepository
import org.crsh.cli.Command
import org.crsh.cli.Option
import org.crsh.cli.Usage
import org.crsh.command.InvocationContext

import java.util.stream.Collectors

@Usage("[UGC] Scheduled tasks")
class task {

    Services services = new Services()
    Repositories repositories = new Repositories()

    @Usage("list all tasks")
    @Command
    def list(InvocationContext context) {
        return repositories.task(context).findAll()
    }

    @Usage("enable tasks")
    @Command
    def enable(
        @Usage("task id")
        @Option(names = ["i", "id"])
            List<Integer> ids,
        @Usage("task name")
        @Option(names = ["n", "name"])
            List<String> names,
        InvocationContext context) {
        return findEnableAndSave(ids, names, repositories.task(context), true)
    }

    @Usage("disable tasks")
    @Command
    def disable(
        @Usage("task id")
        @Option(names = ["i", "id"])
            List<Integer> ids,
        @Usage("task name")
        @Option(names = ["n", "name"])
            List<String> names,
        InvocationContext context) {
        return findEnableAndSave(ids, names, repositories.task(context), false)
    }

    static def findEnableAndSave(List<Integer> ids, List<String> names, TaskRepository taskRepository, boolean enable) {
        Set<Task> changed = []
        Set<Long> set = ids.stream().map({ i -> (long) i }).collect(Collectors.toSet())
        changed.addAll(
            taskRepository.save(
                taskRepository.findAll(set).stream()
                    .peek({ t -> t.setEnabled(true) }).collect(Collectors.toList())
            )
        )
        for (String name : names) {
            taskRepository.findByName(name).ifPresent({ task ->
                task.setEnabled(true)
                taskRepository.save(task)
                changed.add(task)
            })
        }
        return changed
    }
}


