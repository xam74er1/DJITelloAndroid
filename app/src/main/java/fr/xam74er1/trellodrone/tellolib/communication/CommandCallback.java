package fr.xam74er1.trellodrone.tellolib.communication;

/**
 * Callback interface for handling command responses.
 *
 * @param <T> the type of response expected from the command
 */
public interface CommandCallback<T> {
    /**
     * Called when the command is successfully executed.
     *
     * @param response the response from the command execution
     */
    void onSuccess(T response);

    /**
     * Called when an error occurs during the command execution.
     *
     * @param e the exception that occurred
     */
    void onError(Exception e);
}
