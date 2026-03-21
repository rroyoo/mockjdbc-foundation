package io.github.rroyoo.mockjdbc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;

import java.util.Objects;

final class WireMockMappingRegistrar {

    private final WireMockServer wireMockServer;
    private final MockedQueryStubMapper mapper;

    WireMockMappingRegistrar(WireMockServer wireMockServer, MockedQueryStubMapper mapper) {
        this.wireMockServer = Objects.requireNonNull(wireMockServer, "wireMockServer is required");
        this.mapper = Objects.requireNonNull(mapper, "mapper is required");
    }

    boolean upsert(MockedQuery event) {
        var maybeMapping = mapper.map(event);
        if (maybeMapping.isEmpty()) {
            return false;
        }

        var mapping = maybeMapping.get();
        wireMockServer.removeStubMapping(mapping);
        wireMockServer.addStubMapping(mapping);
        return true;
    }
}

