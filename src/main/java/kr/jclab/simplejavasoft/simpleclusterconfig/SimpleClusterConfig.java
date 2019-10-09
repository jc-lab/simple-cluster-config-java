package kr.jclab.simplejavasoft.simpleclusterconfig;

import kr.jclab.simplejavasoft.simpleclusterconfig.modulemanager.ModuleManager;

import java.io.File;
import java.sql.*;

public class SimpleClusterConfig {
    private String jbossModulePath = null;
    private String key = null;
    private String value = null;
    private String operation = null;

    private String dbDriver;
    private String dbUser;
    private String dbPass;
    private String dbUrl;

    private String table;

    private int exitCode = 10;

    static class ArgInfo {
        public final String name;
        public final boolean hasValue;
        public final Handler handler;

        public ArgInfo(String name, boolean hasValue, Handler handler) {
            this.name = "--" + name;
            this.hasValue = hasValue;
            this.handler = handler;
        }

        public interface Handler {
            void input(String value);
        }
    }

    public boolean parseArgs(String[] args) {
        ArgInfo[] argInfos = new ArgInfo[] {
                new ArgInfo("modpath", true, new ArgInfo.Handler() {
                    @Override
                    public void input(String value) {
                        jbossModulePath = value;
                    }
                }),
                new ArgInfo("table", true, new ArgInfo.Handler() {
                    @Override
                    public void input(String value) {
                        table = value;
                    }
                }),
                new ArgInfo("key", true, new ArgInfo.Handler() {
                    @Override
                    public void input(String value) {
                        key = value;
                    }
                }),
                new ArgInfo("value", true, new ArgInfo.Handler() {
                    @Override
                    public void input(String value) {
                        value = value;
                    }
                }),
                new ArgInfo("op", true, new ArgInfo.Handler() {
                    @Override
                    public void input(String value) {
                        operation = value;
                    }
                }),
        };
        int argc = args.length;
        for(int i=0; i<argc; i++) {
            for(ArgInfo argInfo : argInfos) {
                if(args[i].startsWith(argInfo.name + "=")) {
                    argInfo.handler.input(args[i].substring(argInfo.name.length() + 1));
                    break;
                }else if(argInfo.name.equalsIgnoreCase(args[i])) {
                    if(argInfo.hasValue) {
                        i++;
                        argInfo.handler.input(args[i]);
                    }else{
                        argInfo.handler.input(null);
                    }
                    break;
                }
            }
        }

        if(this.jbossModulePath == null) {
            this.jbossModulePath = System.getenv("JBOSS_MODULE_PATH");
        }

        this.dbDriver = System.getenv("DB_DRIVER");
        this.dbUser = System.getenv("DB_USER");
        this.dbPass = System.getenv("DB_PASSWORD");
        this.dbUrl = System.getenv("DB_URL");

        if(this.operation == null) {
            System.err.println("Need operation");
            return false;
        }

        return true;
    }
    public void run() throws ClassNotFoundException, SQLException {
        ModuleManager moduleManager = ModuleManager.getInstance();
        moduleManager.start(new File(this.jbossModulePath));
        Class driverClazz = moduleManager.loadModule(this.dbDriver);
        Connection connection = DriverManager.getConnection(this.dbUrl, this.dbUser, this.dbPass);
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

        try {
            PreparedStatement updatePreparedStatement = null;

            Statement createTableStatment = connection.createStatement();
            createTableStatment.execute("CREATE TABLE IF NOT EXISTS `test` (`ckey` VARCHAR(128) NOT NULL, `value` VARCHAR(256) NULL DEFAULT NULL, PRIMARY KEY (`ckey`))");

            switch (this.operation) {
                case "inc":
                case "increment":
                    if(this.value == null)
                        this.value = "1";
                    updatePreparedStatement = connection.prepareStatement("INSERT INTO `" + this.table + "` (`ckey`, `value`) VALUES (?, CAST(? AS INTEGER)) ON DUPLICATE KEY UPDATE `value` = CAST(`value` AS INTEGER) + 1");
                    updatePreparedStatement.setString(1, this.key);
                    updatePreparedStatement.setString(2, this.value);
                    break;
                case "dec":
                case "decrement":
                    if(this.value == null)
                        this.value = "1";
                    updatePreparedStatement = connection.prepareStatement("INSERT INTO `" + this.table + "` (`ckey`, `value`) VALUES (?, -CAST(? AS INTEGER)) ON DUPLICATE KEY UPDATE `value` = CAST(`value` AS INTEGER) - 1");
                    updatePreparedStatement.setString(1, this.key);
                    updatePreparedStatement.setString(2, this.value);
                    break;
                case "set":
                    updatePreparedStatement = connection.prepareStatement("INSERT INTO `" + this.table + "` (`ckey`, `value`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `value` = ?");
                    updatePreparedStatement.setString(1, this.key);
                    updatePreparedStatement.setString(2, this.value);
                    updatePreparedStatement.setString(3, this.value);
                    break;
                case "get":
                    break;
                default:
                    System.err.println("Need operation");
                    return ;
            }

            if(updatePreparedStatement != null) {
                updatePreparedStatement.execute();
                updatePreparedStatement.close();
            }

            {
                ResultSet resultSet;
                PreparedStatement readPreparedStatement = connection.prepareStatement("SELECT `value` FROM `" + this.table + "` WHERE `ckey`=?");
                readPreparedStatement.setString(1, this.key);
                readPreparedStatement.execute();
                resultSet = readPreparedStatement.getResultSet();
                if (resultSet.next()) {
                    System.out.print(resultSet.getString(1));
                    this.exitCode = 0;
                } else {
                    this.exitCode = 1;
                }
                readPreparedStatement.close();
            }

            connection.commit();
        }catch (Exception e) {
            connection.rollback();
            e.printStackTrace();
        }

        connection.close();
    }
    public static void main(String[] args) throws ClassNotFoundException, SQLException {
        SimpleClusterConfig instance = new SimpleClusterConfig();
        if(instance.parseArgs(new String[] {"--table=test", "--key", "test0001", "--op=set", "--value=abcd"})) {
            instance.run();
        }
        System.exit(instance.exitCode);
    }
}
