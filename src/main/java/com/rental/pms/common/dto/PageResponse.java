package com.rental.pms.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Paginated response wrapper replacing Spring's raw Page object.
 *
 * @param content       items for the current page
 * @param totalElements total number of items across all pages
 * @param totalPages    total number of pages
 * @param currentPage   zero-based current page index
 * @param <T>           type of the page content
 */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int currentPage
) {

    /**
     * Creates a PageResponse from a Spring Data Page.
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber()
        );
    }
}
