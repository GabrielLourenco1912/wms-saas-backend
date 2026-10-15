package dev.lourencogabriel.wmssaas.audit.config;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;
import java.sql.SQLException;

/** RN002: enforce append-only history even for direct SQL on the application connection. */
@Component
public class LedgerProtectionCallback implements Callback {
    @Override public boolean supports(Event event,Context context) { return event==Event.AFTER_MIGRATE; }
    @Override public boolean canHandleInTransaction(Event event,Context context) { return false; }
    @Override public String getCallbackName() { return "protect-stock-ledger"; }
    @Override public void handle(Event event,Context context) {
        try {
            var connection=context.getConnection();
            if(!connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("mysql"))return;
            for(String table:java.util.List.of("transacoes_estoque","historico_tarefas")) {
                for(String action:java.util.List.of("UPDATE","DELETE")) {
                    String name="wms_"+table+"_no_"+action.toLowerCase(java.util.Locale.ROOT);
                    boolean exists;
                    try(var statement=connection.prepareStatement("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME = ?")) {
                        statement.setString(1,name);
                        try(var rows=statement.executeQuery()){rows.next();exists=rows.getInt(1)>0;}
                    }
                    if(!exists)try(var statement=connection.createStatement()) {
                        statement.execute("CREATE TRIGGER "+name+" BEFORE "+action+" ON "+table
                                +" FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable WMS history: append a compensating transaction'");
                    }
                }
            }
        }catch(SQLException exception){throw new IllegalStateException("Cannot install immutable WMS history protection",exception);}
    }
}
