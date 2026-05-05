package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSymbolExtractorTests {
    private final JavaSymbolExtractor extractor = new JavaSymbolExtractor();

    @Test
    void extractsJavaOutlineSymbols() {
        var observation = new ProjectScanObservation("src/main/java/app/UserService.java",
                Domain.ProjectFileType.SOURCE, 200, true, """
                package app;

                public class UserService {
                    private static final String TABLE = "users";
                    private final UserRepository repository;

                    public UserService(UserRepository repository) {
                        this.repository = repository;
                    }

                    public User findUser(String id) {
                        return repository.find(id);
                    }
                }
                """);

        var symbols = extractor.extract(UUID.randomUUID(), UUID.randomUUID(), observation);

        assertThat(symbols).extracting(CodeSymbol::symbolType)
                .contains(Domain.CodeSymbolType.CLASS.name(), Domain.CodeSymbolType.CONSTRUCTOR.name(),
                        Domain.CodeSymbolType.METHOD.name(), Domain.CodeSymbolType.FIELD.name(),
                        Domain.CodeSymbolType.CONSTANT.name());
        assertThat(symbols).extracting(CodeSymbol::symbolName)
                .contains("UserService", "TABLE", "repository", "findUser");
        assertThat(symbols.stream().filter(symbol -> symbol.symbolName().equals("findUser")).findFirst())
                .get()
                .extracting(CodeSymbol::containerName)
                .isEqualTo("UserService");
    }

    @Test
    void extractsRecordsInterfacesAndEnums() {
        var record = new ProjectScanObservation("src/main/java/app/UserView.java",
                Domain.ProjectFileType.SOURCE, 80, true, "public record UserView(String id) {}");
        var iface = new ProjectScanObservation("src/main/java/app/UserPort.java",
                Domain.ProjectFileType.SOURCE, 80, true, "interface UserPort {}");
        var enumType = new ProjectScanObservation("src/main/java/app/UserStatus.java",
                Domain.ProjectFileType.SOURCE, 80, true, "enum UserStatus { ACTIVE }");

        assertThat(extractor.extract(UUID.randomUUID(), UUID.randomUUID(), record))
                .extracting(CodeSymbol::symbolType).contains(Domain.CodeSymbolType.RECORD.name());
        assertThat(extractor.extract(UUID.randomUUID(), UUID.randomUUID(), iface))
                .extracting(CodeSymbol::symbolType).contains(Domain.CodeSymbolType.INTERFACE.name());
        assertThat(extractor.extract(UUID.randomUUID(), UUID.randomUUID(), enumType))
                .extracting(CodeSymbol::symbolType).contains(Domain.CodeSymbolType.ENUM.name());
    }
}
