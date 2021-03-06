package com.mayhew3.drafttower.server.database;

import com.google.common.collect.Lists;
import com.sun.istack.internal.NotNull;

import java.sql.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class DatabaseUtility {
  private static Connection _connection;

  protected static final Logger logger = Logger.getLogger(DatabaseUtility.class.getName());

  public static void initConnection() {
    logger.log(Level.INFO, "Initializing connection.");

    try {
      Class.forName("com.mysql.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      System.out.println("Cannot find MySQL drivers. Exiting.");
      System.exit(-1);
    }

    try {
      String dbpassword = System.getenv().get("dbpassword");
      _connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/uncharted", "root", dbpassword);
    } catch (SQLException e) {
      System.out.println("Cannot connect to database. Exiting.");
      System.exit(-1);
    }
  }

  @NotNull
  protected static ResultSet executeQuery(String sql) {
    try {
      Statement statement = _connection.createStatement();
      return statement.executeQuery(sql);
    } catch (SQLException e) {
      System.out.println("Error running SQL select: " + sql);
      e.printStackTrace();
      System.exit(-1);
    }
    return null;
  }

  @NotNull
  protected static ResultSet executeQueryWithException(String sql) throws SQLException {
    Statement statement = _connection.createStatement();
    return statement.executeQuery(sql);
  }

  @NotNull
  protected static Statement executeUpdate(String sql) {
    try {
      Statement statement = _connection.createStatement();

      statement.executeUpdate(sql);
      return statement;
    } catch (SQLException e) {
      throw new IllegalStateException("Error running SQL select: " + sql);
    }
  }

  @NotNull
  protected static Statement executeUpdateWithException(String sql) throws SQLException {
    Statement statement = _connection.createStatement();

    statement.executeUpdate(sql);
    return statement;
  }

  protected static boolean hasMoreElements(ResultSet resultSet) {
    try {
      return resultSet.next();
    } catch (SQLException e) {
      throw new IllegalStateException("Error fetching next row from result set.");
    }
  }

  protected static int getInt(ResultSet resultSet, String columnName) {
    try {
      return resultSet.getInt(columnName);
    } catch (SQLException e) {
      throw new RuntimeException("Error trying to get integer column " + columnName + ": " + e.getLocalizedMessage());
    }
  }

  protected static String getString(ResultSet resultSet, String columnName) {
    try {
      return resultSet.getString(columnName);
    } catch (SQLException e) {
      throw new IllegalStateException("Error trying to get string column " + columnName);
    }
  }

  protected static boolean columnExists(String tableName, String columnName) {
    try {
      ResultSet tables = _connection.getMetaData().getColumns(null, null, tableName, columnName);
      return tables.next();
    } catch (SQLException e) {
      throw new IllegalStateException("Error trying to find column " + columnName);
    }
  }

  protected static ResultSet prepareAndExecuteStatementFetch(String sql, Object... params) {
    return prepareAndExecuteStatementFetch(sql, Lists.newArrayList(params));
  }

  protected static ResultSet prepareAndExecuteStatementFetch(String sql, List<Object> params) {
    PreparedStatement preparedStatement = prepareStatement(sql, params);
    try {
      return preparedStatement.executeQuery();
    } catch (SQLException e) {
      throw new RuntimeException("Error executing prepared statement for SQL: " + sql + ": " + e.getLocalizedMessage());
    }
  }

  protected static ResultSet prepareAndExecuteStatementFetchWithException(String sql, List<Object> params) throws SQLException {
    PreparedStatement preparedStatement = prepareStatement(sql, params);
    return preparedStatement.executeQuery();
  }

  protected static void prepareAndExecuteStatementUpdate(String sql, Object... params) {
    try {
      PreparedStatement preparedStatement = prepareStatement(sql, Lists.newArrayList(params));

      preparedStatement.executeUpdate();
      preparedStatement.close();
    } catch (SQLException e) {
      throw new RuntimeException("Error preparing statement for SQL: " + sql + ": " + e.getLocalizedMessage());
    }
  }

  protected static void prepareAndExecuteStatementUpdateWithException(String sql, List<Object> params) throws SQLException {
    PreparedStatement preparedStatement = prepareStatement(sql, params);

    preparedStatement.executeUpdate();
    preparedStatement.close();
  }

  protected static PreparedStatement prepareStatement(String sql, List<Object> params) {
    PreparedStatement preparedStatement = getPreparedStatement(sql);
    try {
      return plugParamsIntoStatement(preparedStatement, params);
    } catch (SQLException e) {
      throw new RuntimeException("Error adding parameters to prepared statement for SQL: " + sql + ": " + e.getLocalizedMessage());
    }
  }

  public static PreparedStatement getPreparedStatement(String sql) {
    try {
      return _connection.prepareStatement(sql);
    } catch (SQLException e) {
      throw new RuntimeException("Error preparing statement for SQL: " + sql + ": " + e.getLocalizedMessage());
    }
  }

  protected static ResultSet executePreparedStatementAlreadyHavingParameters(PreparedStatement preparedStatement) {
    try {
      return preparedStatement.executeQuery();
    } catch (SQLException e) {
      throw new RuntimeException("Error executing prepared statement. " + e.getLocalizedMessage());
    }
  }

  public static ResultSet executePreparedStatementWithParams(PreparedStatement preparedStatement, Object... params) {
    List<Object> paramList = Lists.newArrayList(params);
    return executePreparedStatementWithParams(preparedStatement, paramList);
  }

  public static ResultSet executePreparedStatementWithParams(PreparedStatement preparedStatement, List<Object> params) {
    try {
      PreparedStatement statementWithParams = plugParamsIntoStatement(preparedStatement, params);
      return statementWithParams.executeQuery();
    } catch (SQLException e) {
      throw new RuntimeException("Error executing prepared statement with params: " + params + ": " + e.getLocalizedMessage());
    }
  }

  public static void executePreparedUpdateWithParams(PreparedStatement preparedStatement, Object... params) {
    List<Object> paramList = Lists.newArrayList(params);
    try {
      PreparedStatement statementWithParams = plugParamsIntoStatement(preparedStatement, paramList);
      statementWithParams.executeUpdate();
      statementWithParams.close();
    } catch (SQLException e) {
      throw new RuntimeException("Error executing prepared statement with params: " + paramList + ": " + e.getLocalizedMessage());
    }
  }

  public static void executePreparedUpdateWithParamsWithoutClose(PreparedStatement preparedStatement, Object... params) {
    List<Object> paramList = Lists.newArrayList(params);
    try {
      PreparedStatement statementWithParams = plugParamsIntoStatement(preparedStatement, paramList);
      statementWithParams.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Error executing prepared statement with params: " + paramList + ": " + e.getLocalizedMessage());
    }
  }

  private static PreparedStatement plugParamsIntoStatement(PreparedStatement preparedStatement, List<Object> params) throws SQLException {
    int i = 1;
    for (Object param : params) {
      if (param instanceof String) {
        preparedStatement.setString(i, (String) param);
      } else if (param instanceof Integer) {
        preparedStatement.setInt(i, (Integer) param);
      } else {
        throw new RuntimeException("Unknown type of param: " + param.getClass());
      }
      i++;
    }
    return preparedStatement;
  }

  protected static void setString(PreparedStatement preparedStatement, int index, String value) {
    try {
      preparedStatement.setString(index, value);
    } catch (SQLException e) {
      throw new RuntimeException("Error binding parameter " + index + " on statement to value " + value + ": " + e.getLocalizedMessage());
    }
  }

  public static boolean hasConnection() {
    boolean isOpen;
    try {
      isOpen = _connection != null && !_connection.isClosed();
    } catch (SQLException e) {
      return false;
    }
    return isOpen;
  }
}
