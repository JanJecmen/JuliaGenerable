package prlprg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class Generator {

    interface Type {

        @Override
        public String toString();
    }

    static Inst any = new Inst("Any", List.of());
    static Union none = new Union(List.of());

    static boolean isAny(Type t) {
        return t instanceof Inst i && i.nm.equals("Any");
    }

    static boolean isNone(Type t) {
        return t instanceof Union u && u.tys.isEmpty();
    }

    //A construtor instance may have type parameters, examples instances are: Int32 and Vector{Int,N}.
    // When on the LHS of a type declaration, an instance can only have bound variables.
    // When on the RHS of a type declaration an instance can have a mix of instance and variables (bound on LHS of <:).
    // On its own, an instance should not have free variables.  Its name should be defined in the typedb.
    record Inst(String nm, List<Type> tys) implements Type {

        @Override
        public String toString() {
            var args = tys.stream().map(Type::toString).collect(Collectors.joining(","));
            return nm + (tys.isEmpty() ? "" : "{" + args + "}");
        }
    }

    // Each variable occurence, must refer to a bound in an enclosing \Exist. At this point, we should have
    // rejected types with free variables as ill-formed.
    public record Var(Bound b) implements Type {

        @Override
        public String toString() {
            return CodeColors.variables(b.nm());
        }
    }

    // A bound introduces a variable, with an upper and a lower bound. Julia allows writing inconsistent bounds,
    // i.e. !(low <: up). These are meaningless types which cannot be used. We do not check this. We do check
    // that the types are well-formed (no undefined constructor and no free variables)
    record Bound(String nm, Type low, Type up) {

        @Override
        public String toString() {
            return (!isNone(low) ? low + "<:" : "") + CodeColors.light(nm) + (!isAny(up) ? "<:" + up : "");
        }
    }

    // A constant, such as a number, character or string. The implementation of the parser does not attempt
    // do much we constant, they are treated as uninterpreted strings.
    public record Con(String nm) implements Type {

        @Override
        public String toString() {
            return nm;
        }
    }

    record Exist(Bound b, Type ty) implements Type {

        @Override
        public String toString() {
            return CodeColors.standout("∃") + b + CodeColors.standout(".") + ty;
        }
    }

    record Union(List<Type> tys) implements Type {

        @Override
        public boolean equals(Object o) {
            if (o instanceof Union u) {
                return tys.equals(u.tys);
            }
            return false;
        }

        @Override
        public String toString() {
            return "[" + tys.stream().map(Type::toString).collect(Collectors.joining(CodeColors.standout("|"))) + "]";
        }
    }

    public record Tuple(List<Type> tys) implements Type {

        @Override
        public String toString() {
            return "(" + tys.stream().map(Type::toString).collect(Collectors.joining(",")) + ")";
        }
    }

    // A type declaration introduces a new type name, with a type instance and a parent.
    // The type Any has no parent, and is the root of the type hierarchy.
    // We do not print the case where the parent is Any, since it is the default.
    record Decl(String nm, Type ty, Type inst, Decl parent, String src) {

        @Override
        public String toString() {
            var ignore = nm.equals("Any") || this.parent.nm.equals("Any"); // parent is null for Any
            return "type " + nm + " ≡ " + ty + (ignore ? "" : " <: " + inst);
        }
    }

    record Sig(String nm, Type ty, String src) {

        @Override
        public String toString() {
            return "function " + nm + " " + ty;
        }
    }

    GenDB db; // our database of types and signatures
    HashMap<String, InhNode> index = new HashMap<>(); // Map from type names to corresponding node in the inheritance tree

    Generator(GenDB db) {
        this.db = db;
    }

    public void gen() {
        for (var decl : db.tydb.values()) {
            index.put(decl.nm(), new InhNode(decl));
        }
        for (var n : index.values()) {
            n.fixUp();
            System.err.println("Fixed up " + n.name + " " + n.parentName + " " + n.parent.d);
        }
        var d = index.get("Any");
        printHierarchy(d, 0);
        build(d);

        for (var signodes : db.sigdb.values()) {
            for (var n : signodes) {
                try {
                    var s = new Sig(n.nm(), n.ty().toType(new ArrayList<>()), n.src());
                    System.out.println(s);
                } catch (Exception e) {
                    System.err.println("Error: " + n.nm() + " " + e.getMessage());
                    System.err.println(CodeColors.variables("Failed at " + n.src()));
                }
            }
        }
    }

    void printHierarchy(InhNode n, int pos) {
        System.out.println(CodeColors.standout(".").repeat(pos) + n.name);
        for (var c : n.children) {
            printHierarchy(c, pos + 1);
        }
    }

    void build(InhNode n) {
        Decl d;
        try {
            d = n.toDecl();
            System.out.println(d);
        } catch (Exception e) {
            System.err.println("Error: " + n.name + " " + e.getMessage());
            System.err.println("Failed at " + n.decl);
        }
        for (var c : n.children) {
            build(c);
        }
    }

    class InhNode {

        String name;
        GenDB.TyDecl decl;
        Decl d = null;
        InhNode parent = null;
        String parentName;
        List< InhNode> children = new ArrayList<>();

        public InhNode(GenDB.TyDecl d) {
            this.decl = d;
            this.name = d.nm();
            this.parentName = ((GenDB.TyInst) d.parent()).nm();
        }

        void fixUp() {
            System.err.println("Fixing up " + name + " " + parentName + " " + decl);
            if (name.equals("Any")) {
                this.parent = this;
                return;
            }
            var pNode = index.get(parentName);
            assert pNode != null;
            this.parent = pNode;
            pNode.children.add(this);
        }

        Decl toDecl() {
            if (name.equals("Any")) {
                return d = new Decl("Any", any, any, null, "");
            }
            var env = new ArrayList<Bound>();
            var t = decl.ty().toType(env);
            var inst = decl.parent().toType(getBounds(t, env));
            assert parent.d != null;
            return d = new Decl(name, t, inst, parent.d, decl.src());
        }

        // Unwrap the existentials in the type, and return the bounds in the order they appear.
        // Called with a type declaration, so there should only be instances and existentials.
        private List<Bound> getBounds(Type t, List<Bound> env) {
            assert t != null;
            if (t instanceof Inst) {
                return env;
            } else if (t instanceof Exist ty) {
                env.addLast(ty.b());
                return getBounds(ty.ty(), env);
            }
            throw new RuntimeException("Unknown type: " + t);
        }
    }

}
