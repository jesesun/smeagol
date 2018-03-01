package com.cloudogu.smeagol.wiki.infrastructure;

import com.cloudogu.smeagol.Account;
import com.cloudogu.smeagol.wiki.domain.WikiId;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singleton;

@SuppressWarnings("squid:S1160") // ignore multiple exception rule
public class GitClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GitClient.class);

    private final ApplicationEventPublisher publisher;

    private final Account account;
    private final File repository;
    private final URL remoteUrl;
    private final WikiId wikiId;

    private Git gitRepository;

    public GitClient(ApplicationEventPublisher publisher, Account account, File repository, URL remoteUrl, WikiId wikiId) {
        this.publisher = publisher;
        this.account = account;
        this.repository = repository;
        this.remoteUrl = remoteUrl;
        this.wikiId = wikiId;
    }

    public void refresh() throws GitAPIException, IOException {
        // TODO check version and if it is an old repository fire same event as for clone
        if ( repository.exists() ) {
            pullChanges();
        } else {
            createClone();
        }
    }

    private Git open() throws IOException {
        if (gitRepository == null) {
            gitRepository = Git.open(repository);
        }
        return gitRepository;
    }

    public File file(String path) {
        return new File(repository, path);
    }

    public Optional<RevCommit> lastCommit(String path) throws IOException, GitAPIException {
        Git git = open();
        Iterator<RevCommit> iterator = git.log()
                .addPath(path)
                .setMaxCount(1)
                .call()
                .iterator();

        if (iterator.hasNext()) {
            return Optional.of(iterator.next());
        }

        return Optional.empty();
    }

    private void pullChanges()  throws GitAPIException, IOException {
        LOG.trace("open repository {}", repository);
        Git git = open();

        LOG.debug("pull changes from remote for repository {}", repository);
        ObjectId oldHead = git.getRepository().resolve("HEAD^{tree}");

        git.pull()
                .setRemote("origin")
                .setRemoteBranchName(wikiId.getBranch())
                .setCredentialsProvider(credentialsProvider(account))
                .call();

        ObjectId head = git.getRepository().resolve("HEAD^{tree}");
        if (hasRepositoryChanged(oldHead, head)) {
            RepositoryChangedEvent repositoryChangedEvent = createRepositoryChangedEvent(git, oldHead, head);
            if (!repositoryChangedEvent.isEmpty()) {
                publisher.publishEvent(repositoryChangedEvent);
            }
        }
    }

    private RepositoryChangedEvent createRepositoryChangedEvent(Git git, ObjectId oldHead, ObjectId head) throws IOException, GitAPIException {
        ObjectReader reader = git.getRepository().newObjectReader();

        CanonicalTreeParser newTree = new CanonicalTreeParser();
        newTree.reset(reader, head);

        CanonicalTreeParser oldTree = new CanonicalTreeParser();
        oldTree.reset(reader, oldHead);

        // TODO add path filter for wiki docs path

        List<DiffEntry> diffs = git.diff()
                .setNewTree(newTree)
                .setOldTree(oldTree)
                .call();

        RepositoryChangedEvent event = new RepositoryChangedEvent(wikiId);
        for (DiffEntry entry : diffs) {
            if (isPageDiff(entry)) {
                addChangeToEvent(entry, event);
            }
        }
        return event;
    }

    private boolean isPageDiff(DiffEntry entry) {
        return Pages.isPageFilename(entry.getNewPath()) || Pages.isPageFilename(entry.getOldPath());
    }

    private void addChangeToEvent(DiffEntry entry, RepositoryChangedEvent event) {
        switch (entry.getChangeType()) {
            case ADD:
            case COPY:
                event.added(entry.getNewPath());
                break;
            case DELETE:
                event.deleted(entry.getOldPath());
                break;
            case MODIFY:
                event.modified(entry.getNewPath());
                break;
            case RENAME:
                event.deleted(entry.getOldPath());
                event.added(entry.getNewPath());
        }
    }

    private boolean hasRepositoryChanged(ObjectId oldHead, ObjectId head) {
        return ! head.equals(oldHead);
    }

    private void createClone()  throws GitAPIException, IOException {
        String branch = wikiId.getBranch();
        LOG.info("clone repository {} to {}", remoteUrl, repository);
        gitRepository = Git.cloneRepository()
                .setURI(remoteUrl.toExternalForm())
                .setDirectory(repository)
                .setBranchesToClone(singleton("refs/head" + branch))
                .setBranch(branch)
                .setCredentialsProvider(credentialsProvider(account))
                .call();

        if (!"master".equals(branch)) {
            File newRef = new File(repository, ".git/refs/heads/master");
            File refDirectory = newRef.getParentFile();
            if (!refDirectory.exists() && !refDirectory.mkdirs()) {
                throw new IOException("failed to create parent directory " + refDirectory);
            }
            if (!newRef.exists() && !newRef.createNewFile()) {
                throw new IOException("failed to create parent directory");
            }
            try (BufferedWriter output = new BufferedWriter(new FileWriter(newRef))) {
                output.write("ref: refs/heads/" + branch);
            }
        }

        // TODO add path filter for wiki docs path

        URI repositoryUri = repository.toURI();

        RepositoryChangedEvent repositoryChangedEvent = new RepositoryChangedEvent(wikiId);
        Files.walkFileTree(repository.toPath(), new SimpleFileVisitor<java.nio.file.Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (".git".equals(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs) {
                String path = repositoryUri.relativize(file.toUri()).getPath();
                if (Pages.isPageFilename(path)) {
                    repositoryChangedEvent.added(path);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (!repositoryChangedEvent.isEmpty()) {
            publisher.publishEvent(repositoryChangedEvent);
        }
    }


    private CredentialsProvider credentialsProvider(Account account) {
        return new UsernamePasswordCredentialsProvider(account.getUsername(), account.getPassword());
    }

    public RevCommit commit(String path, String displayName, String email, String message) throws GitAPIException, IOException {
        String[] paths = {path};
        return commit(paths, displayName, email, message);
    }

    public RevCommit commit(String[] paths, String displayName, String email, String message) throws GitAPIException, IOException {
        Git git = open();

        for(String path : paths ) {
            git.add()
                    .addFilepattern(path)
                    .call();
        }

        RevCommit commit = git.commit()
                .setAuthor(displayName, email)
                .setMessage(message)
                .call();

        pushChanges();

        return commit;
    }

    private void pushChanges() throws GitAPIException, IOException {
        String branch = wikiId.getBranch();
        CredentialsProvider credentials = credentialsProvider(account);

        Git git = open();

        LOG.info("push changes to remote {} on branch {}", remoteUrl, branch);
        git.push()
                .setRemote("origin")
                .setRefSpecs(new RefSpec(branch+":"+branch))
                .setCredentialsProvider(credentials)
                .call();
    }


    @Override
    public void close()  {
        if (gitRepository != null) {
            gitRepository.close();
        }
    }
}
