package com.cloudogu.smeagol.wiki.usecase;

import com.cloudogu.smeagol.AccountService;
import com.cloudogu.smeagol.wiki.domain.Commit;
import com.cloudogu.smeagol.wiki.domain.Page;
import com.cloudogu.smeagol.wiki.domain.PageRepository;
import com.cloudogu.smeagol.wiki.domain.Path;
import de.triology.cb.CommandHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.cloudogu.smeagol.wiki.usecase.Commits.createNewCommit;

/**
 * Handler for {@link EditPageCommand}.
 */
@Component
public class EditPageCommandHandler implements CommandHandler<Page, EditPageCommand> {

    private final PageRepository repository;
    private final AccountService accountService;

    @Autowired
    public EditPageCommandHandler(PageRepository repository, AccountService accountService) {
        this.repository = repository;
        this.accountService = accountService;
    }

    @Override
    public Page handle(EditPageCommand command) {
        Path path = command.getPath();
        Page page = repository.findByWikiIdAndPath(command.getWikiId(), path)
                .orElseThrow(() -> new PageNotFoundException(path, "page not found"));

        Commit commit = createNewCommit(accountService, command.getMessage());
        page.edit(commit, command.getContent());

        return repository.save(page);
    }
}
