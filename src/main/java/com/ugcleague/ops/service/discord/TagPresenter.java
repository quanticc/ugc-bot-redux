package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.Tag;
import com.ugcleague.ops.repository.mongo.TagRepository;
import com.ugcleague.ops.service.DiscordCacheService;
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
import java.util.*;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static java.util.Arrays.asList;

/**
 * Commands to manage tagged messages.
 * <ul>
 * <li>tag</li>
 * </ul>
 */
@Service
public class TagPresenter {

    private static final Logger log = LoggerFactory.getLogger(TagPresenter.class);

    private final CommandService commandService;
    private final DiscordCacheService cacheService;
    private final TagRepository tagRepository;

    private OptionSpec<String> tagAddSpec;
    private OptionSpec<String> tagRemoveSpec;
    private OptionSpec<String> tagInfoSpec;
    private OptionSpec<String> tagNonOptionSpec;

    @Autowired
    public TagPresenter(CommandService commandService, DiscordCacheService cacheService, TagRepository tagRepository) {
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
            .support().originReplies().parser(parser).withOptionAliases(aliases).command(this::executeTag)
            .limit(3).build());
    }

    private String executeTag(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        if (Arrays.asList(optionSet.has(tagAddSpec), optionSet.has(tagRemoveSpec), optionSet.has(tagInfoSpec)).stream()
            .filter(b -> b).count() > 1) {
            return "Please run in separate commands";
        }

        List<String> nonOptions = optionSet.valuesOf(tagNonOptionSpec);

        if (optionSet.has(tagAddSpec)) {
            String key = optionSet.valueOf(tagAddSpec).replaceAll("\"|'", "");
            Optional<Tag> tag = tagRepository.findById(key);
            if (tag.isPresent()) {
                return "A tag with this name already exists";
            }
            if (nonOptions.isEmpty()) {
                return "Must add some content to this tag";
            }
            String content = mergeNonOptions(nonOptions);
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

        if (!nonOptions.isEmpty()) {
            String key = mergeNonOptions(nonOptions);
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
        return nonOptions.stream().map(s -> {
            if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                return s;
            } else {
                return s + " ";
            }
        }).collect(Collectors.joining()).trim();
    }
}
