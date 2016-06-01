package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.Tag;
import com.ugcleague.ops.repository.mongo.TagRepository;
import com.ugcleague.ops.service.DiscordCacheService;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newAliasesMap;
import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static java.util.Arrays.asList;

/**
 * Commands to manage tagged messages.
 * <ul>
 * <li>tag</li>
 * </ul>
 */
@Service
@Transactional
public class TagPresenter {

    private static final Logger log = LoggerFactory.getLogger(TagPresenter.class);

    private final CommandService commandService;
    private final DiscordCacheService cacheService;
    private final TagRepository tagRepository;
    private final Map<Tag, Command> replyCommands = new ConcurrentHashMap<>();

    private OptionSpec<String> tagAddSpec;
    private OptionSpec<String> tagRemoveSpec;
    private OptionSpec<String> tagInfoSpec;
    private OptionSpec<String> tagNonOptionSpec;
    private OptionSpec<String> aliasNonOptionSpec;
    private OptionSpec<String> aliasSetSpec;
    private OptionSpec<String> aliasResetSpec;
    private OptionSpec<String> directEnableSpec;
    private OptionSpec<String> directDisableSpec;
    private OptionSpec<String> tagEditSpec;
    private OptionSpec<Boolean> tagGlobalSpec;
    private OptionSpec<Boolean> aliasGlobalSpec;

    @Autowired
    public TagPresenter(CommandService commandService, DiscordCacheService cacheService, TagRepository tagRepository) {
        this.commandService = commandService;
        this.cacheService = cacheService;
        this.tagRepository = tagRepository;
    }

    @PostConstruct
    private void configure() {
        initTagCommand();
        initAliasCommand();
        initDirectCommand();

        for (Tag tag : tagRepository.findByDirect(true)) {
            replyCommands.put(tag, registerReplyCommand(tag));
        }
    }

    private Command registerReplyCommand(Tag tag) {
        Tag t = tag.getParent() != null ? tagRepository.findById(tag.getParent()).get() : tag;
        return commandService.register(CommandBuilder.anyMatch(tag.getId())
            .description("Display the tag: " + tag.getId()).unrestricted().originReplies().noParser()
            .command((m, o) ->
                t.getContent() + " " + m.getContent().substring(tag.getId().length()).trim()
                    + " -" + m.getAuthor().getName()
            ).build());
    }

    private void initTagCommand() {
        OptionParser parser = newParser();
        tagNonOptionSpec = parser.nonOptions("Tagged message contents, only used in add mode").ofType(String.class);
        tagAddSpec = parser.accepts("create", "Adds a new tagged message")
            .withRequiredArg().describedAs("key");
        tagEditSpec = parser.accepts("update", "Edit an existing tagged message")
            .withRequiredArg().describedAs("key");
        tagRemoveSpec = parser.accepts("delete", "Remove an existing tag")
            .withRequiredArg().describedAs("key");
        tagInfoSpec = parser.accepts("info", "Retrieves info about an existing tag")
            .withRequiredArg().describedAs("key");
        tagGlobalSpec = parser.accepts("global", "Make a tag global to all servers")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        commandService.register(CommandBuilder.anyMatch(".tag").description("Perform operations to tag and display messages")
            .unrestricted().originReplies().parser(parser).optionAliases(newAliasesMap(parser)).command(this::executeTag)
            .limit(3).build());
    }

    private void initAliasCommand() {
        // .tag-alias set <key> <target>
        // .tag-alias reset <key>
        OptionParser parser = newParser();
        aliasNonOptionSpec = parser.nonOptions("The target tag assigned, only used in add mode").ofType(String.class);
        aliasSetSpec = parser.accepts("set", "Sets a tag as alias of another one")
            .withRequiredArg().describedAs("key");
        aliasResetSpec = parser.accepts("reset", "Resets the alias status of a tag")
            .withRequiredArg().describedAs("key");
        aliasGlobalSpec = parser.accepts("global", "Make alias global to all servers")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        commandService.register(CommandBuilder.anyMatch(".tag-alias").description("Sets/Resets aliases to a tag")
            .support().originReplies().parser(parser).optionAliases(newAliasesMap(parser)).command(this::executeAlias)
            .limit(3).build());
    }

    private void initDirectCommand() {
        // .tag-direct enable <key>
        // .tag-direct disable <key>
        OptionParser parser = newParser();
        directEnableSpec = parser.acceptsAll(asList("e", "enable"), "Enable tag direct invocation mode")
            .withRequiredArg().describedAs("key");
        directDisableSpec = parser.acceptsAll(asList("d", "disable"), "Disable tag direct invocation mode")
            .withRequiredArg().describedAs("key");
        Map<String, String> aliases = newAliasesMap();
        aliases.put("enable", "-e");
        aliases.put("disable", "-d");
        commandService.register(CommandBuilder.anyMatch(".tag-direct").description("Configures a tag as direct mode")
            .support().originReplies().parser(parser).optionAliases(aliases).command(this::executeDirect)
            .limit(2).build());
    }

    private String executeTag(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        if (Arrays.asList(optionSet.has(tagAddSpec), optionSet.has(tagEditSpec), optionSet.has(tagRemoveSpec),
            optionSet.has(tagInfoSpec)).stream().filter(b -> b).count() > 1) {
            return "Please run as separate commands";
        }

        List<String> nonOptions = optionSet.valuesOf(tagNonOptionSpec);
        boolean global = optionSet.has(tagGlobalSpec) && optionSet.valueOf(tagGlobalSpec);

        if (optionSet.has(tagAddSpec)) {
            String key = optionSet.valueOf(tagAddSpec).replaceAll("\"|'", "");
            Optional<Tag> tag = tagRepository.findById(key);
            if (tag.isPresent()) {
                return "A tag with this name already exists";
            }
            if (nonOptions.isEmpty()) {
                return "Must add some content to this tag: `.tag add <name> <content>`";
            }
            String content = mergeNonOptions(nonOptions);
            Tag newTag = new Tag();
            newTag.setId(key);
            newTag.setAuthor(cacheService.getOrCreateUser(message.getAuthor()));
            if (!message.getChannel().isPrivate()) {
                newTag.setGuild(cacheService.getOrCreateGuild(message.getGuild()));
            }
            newTag.setContent(content);
            newTag.setGlobal(global);
            log.debug("Saving new tag: {}", newTag);
            tagRepository.save(newTag);
            return "Tag '" + key + "' added";
        }

        if (optionSet.has(tagEditSpec)) {
            String key = optionSet.valueOf(tagEditSpec).replaceAll("\"|'", "");
            Optional<Tag> tag = tagRepository.findById(key);
            if (!tag.isPresent()) {
                return "No tag exists with this name";
            }
            if (nonOptions.isEmpty()) {
                return "Must add some content to this tag: `.tag edit <name> <content>`";
            }
            String content = mergeNonOptions(nonOptions);
            tag.get().setAuthor(cacheService.getOrCreateUser(message.getAuthor()));
            tag.get().setContent(content);
            if (!message.getChannel().isPrivate()) {
                tag.get().setGuild(cacheService.getOrCreateGuild(message.getGuild()));
            }
            if (optionSet.has(tagGlobalSpec)) {
                tag.get().setGlobal(global);
            }
            log.debug("Updating tag: {}", tag.get());
            tagRepository.save(tag.get());
            // recreate reply command if existed
            if (replyCommands.containsKey(tag.get())) {
                commandService.unregister(replyCommands.remove(tag.get()));
                replyCommands.put(tag.get(), registerReplyCommand(tag.get()));
            }
            return "Tag '" + key + "' updated";
        }

        if (optionSet.has(tagRemoveSpec)) {
            String key = optionSet.valueOf(tagRemoveSpec);
            Optional<Tag> tag = tagRepository.findById(key);
            if (!tag.isPresent()) {
                return "No tag exists with this name";
            }

            List<Tag> aliases = tagRepository.findByParent(tag.get().getId());

            for (Tag alias : aliases) {
                log.debug("Deleting alias: {}", alias.getId());
                tagRepository.delete(alias);
            }

            log.debug("Deleting tag: {}", key);
            tagRepository.delete(key);
            // remove reply command if existed
            if (replyCommands.containsKey(tag.get())) {
                commandService.unregister(replyCommands.remove(tag.get()));
            }
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

        IGuild guild = (message.getChannel().isPrivate() ? null : message.getGuild());

        if (!nonOptions.isEmpty()) {
            String key = mergeNonOptions(nonOptions);
            Optional<Tag> tag = tagRepository.findById(key);
            if (!tag.isPresent()) {
                String fuzzy = fuzzyTagsById(key, guild);
                return "No tag exists with this name." + (fuzzy.isEmpty() ? "" : " Perhaps you meant: " + fuzzy);
            }
            return tag.get().getContent();
        }

        return "Available definitions: " + tagRepository.findAll().stream()
            .filter(t -> isGlobalOrMatchesGuild(t, guild))
            .map(Tag::getId).collect(Collectors.joining(", "));
    }

    private String fuzzyTagsById(String src, IGuild guild) {
        return tagRepository.findAll().stream()
            .filter(t -> isGlobalOrMatchesGuild(t, guild))
            .map(Tag::getId)
            .filter(t -> StringUtils.getLevenshteinDistance(src, t, 5) > 0)
            .collect(Collectors.joining(", "));
    }

    private boolean isGlobalOrMatchesGuild(Tag tag, IGuild guild) {
        return guild == null || tag.getGuild() == null || tag.isGlobal() || guild.getID().equals(tag.getGuild().getId());
    }

    private String executeAlias(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        if (optionSet.has(aliasSetSpec) && optionSet.has(aliasResetSpec)) {
            return "Please run as separate commands";
        }

        List<String> nonOptions = optionSet.valuesOf(aliasNonOptionSpec);
        boolean global = optionSet.has(aliasGlobalSpec) && optionSet.valueOf(aliasGlobalSpec);

        if (optionSet.has(aliasSetSpec)) {
            String key = optionSet.valueOf(aliasSetSpec).replaceAll("\"|'", "");
            Optional<Tag> tag = tagRepository.findById(key);
            if (tag.isPresent()) {
                return "The tag '" + key + "' already exists";
            }
            if (!tagRepository.findByParent(key).isEmpty()) {
                return "The alias '" + key + "' already exists";
            }
            if (nonOptions.isEmpty()) {
                return "You must specify the target tag: `.tag-alias set <source> <target>`";
            }
            String target = mergeNonOptions(nonOptions);
            Optional<Tag> targetTag = tagRepository.findById(target);
            if (!targetTag.isPresent()) {
                return "The target tag '" + target + "' does not exist, create it first with `.tag add \"" + target + "\" <content>`";
            }
            if (targetTag.get().getParent() != null) {
                return "Target tag '" + target + "' must not be an alias";
            }
            Tag newTag = new Tag();
            newTag.setId(key);
            newTag.setAuthor(cacheService.getOrCreateUser(message.getAuthor()));
            if (!message.getChannel().isPrivate()) {
                newTag.setGuild(cacheService.getOrCreateGuild(message.getGuild()));
            }
            newTag.setContent("");
            newTag.setGlobal(global);
            newTag.setParent(targetTag.get().getId());
            log.debug("Saving new tag: {}", newTag);
            tagRepository.save(newTag);
            return "Alias '" + key + "' of tag '" + targetTag.get().getId() + "' added";
        }

        if (optionSet.has(aliasResetSpec)) {
            String key = optionSet.valueOf(aliasResetSpec).replaceAll("\"|'", "");
            Optional<Tag> tag = tagRepository.findById(key);
            if (!tag.isPresent()) {
                return "No alias exists with this name";
            }
            boolean empty = tag.get().getContent() == null || tag.get().getContent().isEmpty();
            if (empty) {
                log.debug("Deleting alias: {}", key);
                tagRepository.delete(key);
                return "Alias '" + key + "' removed";
            } else {
                log.debug("Converting alias to simple tag: {}", key);
                tag.get().setParent(null);
                tagRepository.save(tag.get());
                return "Alias '" + key + "' reset to tag";
            }
        }

        return "Available aliases: " + tagRepository.findAll().stream()
            .filter(t -> t.getParent() != null)
            .map(t -> t.getId() + " => " + t.getParent())
            .collect(Collectors.joining(", "));
    }

    private String executeDirect(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        if (optionSet.has(directEnableSpec) && optionSet.has(directDisableSpec)) {
            return "Please run as separate commands";
        }

        if (optionSet.has(directEnableSpec)) {
            String key = optionSet.valueOf(directEnableSpec).replaceAll("\"|'", "");
            Optional<Tag> tag = tagRepository.findById(key);
            if (!tag.isPresent()) {
                return "No tag exists with this name";
            }
            if (key.startsWith(".") || commandService.getCommandList().stream().anyMatch(c -> c.matches(key))) {
                return "This tag can't be used as reply trigger";
            }
            if (tag.get().isDirect()) {
                return "This tag is already set to direct mode";
            }
            tag.get().setDirect(true);
            log.debug("Enabling reply on message (direct) mode: {}", key);
            Tag saved = tagRepository.save(tag.get());
            replyCommands.put(saved, registerReplyCommand(saved));
            return "Tag '" + key + "' will now be triggered on normal messages";
        }

        if (optionSet.has(directDisableSpec)) {
            String key = optionSet.valueOf(directDisableSpec).replaceAll("\"|'", "");
            Optional<Tag> tag = tagRepository.findById(key);
            if (!tag.isPresent()) {
                return "No tag exists with this name";
            }
            if (!tag.get().isDirect()) {
                return "This tag is not configured to direct mode";
            }
            tag.get().setDirect(false);
            log.debug("Disabling reply on message (direct) mode: {}", key);
            tagRepository.save(tag.get());
            commandService.unregister(replyCommands.remove(tag.get()));
            return "Tag '" + key + "' will no longer be triggered on normal messages";
        }

        return "Available tags replying on messages starting with: " + tagRepository.findAll().stream()
            .filter(Tag::isDirect).map(Tag::getId).collect(Collectors.joining(", "));
    }

    private String formatTagContents(Tag tag) {
        boolean isAlias = tag.getParent() != null;
        return String.format("Tag: %s\nAuthor: %s (%s)\nCreated: %s\nContent: %s\n", tag.getId(),
            tag.getAuthor().getName(), tag.getAuthor().getId(), tag.getCreatedDate(), isAlias ? "-- is an alias of --\n" +
                formatTagContents(tagRepository.findById(tag.getParent()).get()) : tag.getContent());
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
