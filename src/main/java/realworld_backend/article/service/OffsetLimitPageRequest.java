package realworld_backend.article.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.io.Serial;
import java.io.Serializable;

public final class OffsetLimitPageRequest implements Pageable, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int offset;
    private final int limit;
    private final Sort sort;

    private OffsetLimitPageRequest(int offset, int limit, Sort sort) {
        this.offset = offset;
        this.limit = limit;
        this.sort = sort == null ? Sort.unsorted() : sort;
    }

    public static OffsetLimitPageRequest of(int offset, int limit, Sort sort) {
        if (offset < 0) {
            throw new BizException(ErrorCode.OFFSET_MUST_BE_LARGGER_THAN_0);
        }
        if (limit < 1) {
            throw new BizException(ErrorCode.LIMITED_MUST_BE_LARGGER_THAN_0);
        }
        return new OffsetLimitPageRequest(offset, limit, sort);
    }

    @Override
    public int getPageNumber() {
        return offset / limit;
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetLimitPageRequest(offset + limit, limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        if (!hasPrevious()) {
            return first();
        }
        return new OffsetLimitPageRequest(offset - limit, limit, sort);
    }

    @Override
    public Pageable first() {
        return new OffsetLimitPageRequest(0, limit, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must be >= 0");
        }
        return new OffsetLimitPageRequest(pageNumber * limit, limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
