package com.cloudogu.smeagol.wiki.domain;

/**
 * SearchResultRepository is responsible for search pages within a wiki.
 */
public interface SearchResultRepository {

    /**
     * Search the wiki by the given query.
     *
     * @param wikiId id of wiki
     * @param query search query
     *
     * @return search results
     */
    Iterable<SearchResult> search(WikiId wikiId, String query);

}
