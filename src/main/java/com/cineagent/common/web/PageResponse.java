package com.cineagent.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The one shape every paginated endpoint returns. Spring Data's {@link Page} is never serialized
 * directly — its JSON shape isn't a stable contract across Spring versions (see the
 * {@code api-contract} skill).
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isLast());
  }

  public static <T, R> PageResponse<R> from(Page<T> page, List<R> mappedContent) {
    return new PageResponse<>(
        mappedContent,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isLast());
  }
}
