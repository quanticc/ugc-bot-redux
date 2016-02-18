package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.Tag;
import com.ugcleague.ops.repository.mongo.TagRepository;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static java.util.Arrays.asList;

@Service
public class TagCommand {

    private static final Logger log = LoggerFactory.getLogger(TagCommand.class);

    private final CommandService commandService;
    private final DiscordCacheService cacheService;
    private final TagRepository tagRepository;

    private OptionSpec<String> tagAddSpec;
    private OptionSpec<String> tagRemoveSpec;
    private OptionSpec<String> tagInfoSpec;
    private OptionSpec<String> tagNonOptionSpec;

    @Autowired
    public TagCommand(CommandService commandService, DiscordCacheService cacheService, TagRepository tagRepository) {
        this.commandService = commandService;
        this.cacheService = cacheService;
        this.tagRepository = tagRepository;
    }

    @PostConstruct
    private void configure() {
        OptionParser parser = newParser();
        tagNonOptionSpec = parser.nonOptions("Tagged message contents, ignored in all modes except add").ofType(String.class);
        tagAddSpec = parser.acceptsAll(asList("a", "add", "create"), "Adds a new tagged message")
            .withRequiredArg().describedAs("key");
        tagRemoveSpec = parser.acceptsAll(asList("r", "remove", "delete"), "Remove an existing tag")
            .withRequiredArg().describedAs("key");
        tagInfoSpec = parser.acceptsAll(asList("i", "info"), "Retrieves info about an existing tag")
            .withRequiredArg().describedAs("key");
        Map<String, String> aliases = new HashMap<>();
        aliases.put("add", "-a");
        aliases.put("remove", "-r");
        aliases.put("create", "-a");
        aliases.put("delete", "-r");
        aliases.put("info", "-i");
        commandService.register(CommandBuilder.anyMatch(".tag").description("Perform operations to tag and display messages")
            .support().originReplies().parser(parser).withOptionAliases(aliases).command(this::executeTag).build());
    }

    private String executeTag(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        /*
        valid invocations:
            .tag add <key> <message....>
                non-options must be collapsed into one
            .tag remove <key>
            .tag info <key>
                non-options will be ignored
            .tag <key>
                rest of non-options will be ignored
         */

        if (optionSet.has(tagAddSpec)) {
            String key = optionSet.valueOf(tagAddSpec);
            Optional<Tag> tag = tagRepository.findById(key);
            if (tag.isPresent()) {
                return "A tag with this name already exists";
            }
            String content = mergeNonOptions(optionSet.valuesOf(tagNonOptionSpec));
            Tag newTag = new Tag(key, cacheService.getOrCreateUser(message.getAuthor()), content);
            log.debug("Saving new tag: {}", newTag);
            tagRepository.save(newTag);
            return "Tag '" + key + "' added";
        }

        if (optionSet.has(tagRemoveSpec)) {
            String key = optionSet.valueOf(tagRemoveSpec);
            Optional<Tag> tag = tagRepository.findById(key);
            if (!tag.isPresent()) {
                return "No tag exists with this name";
            }
            log.debug("Deleting tag: {}", key);
            tagRepository.delete(key);
            return "Tag '" + key + "' removed";
        }

        if (optionSet.has(tagInfoSpec)) {
            String key = optionSet.valueOf(tagInfoSpec);
            Optional<Tag> tag = tagRepository.findById(key);
            if (!tag.isPresent()) {
                return "No tag exists with this name";
            }
            return formatTagContents(tag.get());
        }

        List<String> nonOptions = optionSet.valuesOf(tagNonOptionSpec);

        if (!nonOptions.isEmpty()) {
            String key = nonOptions.get(0);
            Optional<Tag> tag = tagRepository.findById(key);
            if (!tag.isPresent()) {
                return "No tag exists with this name";
            }
            return tag.get().getContent();
        }

        return "Available definitions: " + tagRepository.findAll().stream()
            .map(Tag::getId).collect(Collectors.joining(", "));
    }

    private String formatTagContents(Tag tag) {
        return String.format("Tag: %s\nAuthor: %s (%s)\nCreated: %s\nContent: %s\n", tag.getId(),
            tag.getAuthor().getName(), tag.getAuthor().getId(), tag.getCreatedDate(), tag.getContent());
    }

    private String mergeNonOptions(List<String> nonOptions) {
        return nonOptions.stream().collect(Collectors.joining(" "));
    }
}
