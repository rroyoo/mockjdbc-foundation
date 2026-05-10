package io.github.rroyoo.mockjdbc.wiremock;

import com.github.tomakehurst.wiremock.core.Admin;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;

import java.util.Objects;

final class WireMockMappingRegistrar {

    private final Admin admin;
    private final MockedQueryStubMapper mapper;

    WireMockMappingRegistrar(Admin admin, MockedQueryStubMapper mapper) {
        this.admin = Objects.requireNonNull(admin, "admin is required");
        this.mapper = Objects.requireNonNull(mapper, "mapper is required");
    }

    boolean upsert(MockedQuery event) {
        var maybeMapping = mapper.map(event);
        if (maybeMapping.isEmpty()) {
            return false;
        }

        var mapping = maybeMapping.get();
        admin.removeStubMapping(mapping);
        admin.addStubMapping(mapping);
        return true;
    }
}

