package com.mayhew3.drafttower.server.database.dataobject;

public abstract class FieldConversion<T> {
  abstract T parseFromString(String value) throws NumberFormatException;
}
