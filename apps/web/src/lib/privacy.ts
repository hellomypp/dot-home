/** Avoid emitting invitation capabilities in development proxy/redirect error logs. */
export function redactTokenPaths(message:string){
 return message.replace(/((?:\/|%2f)(?:invite|visit|api(?:\/|%2f)(?:invitations|visits))(?:\/|%2f))[A-Za-z0-9_-]{43}/gi,'$1[redacted]');
}
