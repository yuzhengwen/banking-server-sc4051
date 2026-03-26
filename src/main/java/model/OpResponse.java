package model;

import java.util.function.Function;

public record OpResponse<T>(T data, StatusCode status) {

    public boolean isSuccess() {
        return status == StatusCode.STATUS_OK;
    }

    // Static helpers
    public static <T> OpResponse<T> ok(T data) {
        return new OpResponse<>(data, StatusCode.STATUS_OK);
    }

    public static <T> OpResponse<T> error(StatusCode code) {
        return new OpResponse<>(null, code);
    }

    // allows chaining
    public <R> OpResponse<R> then(Function<T, OpResponse<R>> nextStep) {
        if (status != StatusCode.STATUS_OK) {
            return OpResponse.error(this.status);
        }
        return nextStep.apply(data);
    }

    public <R> OpResponse<R> map(Function<T, R> mapper) {
        if (status != StatusCode.STATUS_OK) {
            // If this one failed, pass the error along to the next type
            return OpResponse.error(this.status);
        }
        // If it succeeded, run the next step
        return OpResponse.ok(mapper.apply(data));
    }
}