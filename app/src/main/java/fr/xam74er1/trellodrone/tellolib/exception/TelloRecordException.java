package fr.xam74er1.trellodrone.tellolib.exception;

/**
 * Exception thrown for communication errors.
 */
public class TelloRecordException extends RuntimeException
{
    private static final long serialVersionUID = 2L;

    public TelloRecordException(String message)
    {
        super(message);
    }

    public TelloRecordException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public TelloRecordException(Throwable cause)
    {
        super(cause.getMessage(), cause);
    }

}
