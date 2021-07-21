package com.castsoftware.aip.console.tools.core.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DatabaseConnectionSettingsDto {
    private String guid;
    private String host;
    private int port;
    private String userName;
    private String password;
    private String databaseName;
    private boolean sslEnabled;

    public String getServerName() {
        return host + ":" + port;
    }
}
