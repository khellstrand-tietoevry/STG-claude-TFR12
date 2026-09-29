package com.tfi.tfr12.phase1.errormessage;

/**
 * Raised when the underlying message-text store could not be read for a
 * reason other than "no matching row" - the legacy equivalent of D310-SQL
 * hitting a nonzero, non-100 SQLCODE (F7918030.src Z800-SQL-Abend).
 */
public class MessageRepositoryFailureException extends RuntimeException {
    public MessageRepositoryFailureException(String message) {
        super(message);
    }
}
