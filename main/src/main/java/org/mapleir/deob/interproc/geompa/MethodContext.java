import java.util.Map;

public final class MethodContext implements MethodOrMethodContext
{ 
    private SootMethod method;
    @Override
	public SootMethod method() { return method; }
    private Context context;
    @Override
	public Context context() { return context; }
    private MethodContext( SootMethod method, Context context ) {
        this.method = method;
        this.context = context;
    }
    @Override
	public int hashCode() {
        return method.hashCode() + context.hashCode();
    }
    @Override
	public boolean equals( Object o ) {
        if( o instanceof MethodContext ) {
            MethodContext other = (MethodContext) o;
            return method.equals( other.method ) && context.equals( other.context );
        }
        return false;
    }
    public static MethodOrMethodContext v( SootMethod method, Context context ) {
        if( context == null ) return method;
        MethodContext probe = new MethodContext( method, context );
        Map<MethodContext, MethodContext> map = G.v().MethodContext_map;
        MethodContext ret = map.get( probe );
        if( ret == null ) {
            map.put( probe, probe );
            return probe;
        }
        return ret;
    }
    @Override
	public String toString() {
        return "Method "+method+" in context "+context;
    }
}