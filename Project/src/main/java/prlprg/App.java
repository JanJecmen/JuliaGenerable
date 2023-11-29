package prlprg;

public class App {

    static boolean debug = false;
    static GenDB db = new GenDB();
    static String dir, types, functions;
    static String[] defaultArgs = {"-d", // run micro tests
        "-c NONE", // color the output
        "-r ../Inputs/", // root directory with input files
        "-f raicode_functions.jlg", // file with function signatures
        "-t raicode_types.jlg"}; // file with type declarations

    public static void main(String[] args) {
        parseArgs(args);
        var p = new Parser();
        p = debug ? p.withString(tstr) : p.withFile(dir + types);
        p.addLines(addType); // adding some builtin types (see below)
        p.tokenize();
        while (!p.peek().isEOF()) {
            db.addTyDecl(TypeDeclaration.parse(p).toTy());
        }
        p = new Parser();
        p = debug ? p.withString(str) : p.withFile(dir + functions);
        p.tokenize();
        while (!p.peek().isEOF()) {
            db.addSig(Function.parse(p).toTy());
        }
        db.cleanUp();
        new Generator(db).gen();
    }

    static String tstr = """
      struct A{T<:B{<:C}} <: F end
      struct N{T<:Tuple{}, A<:L{Tuple{}}} <: I{T<:Tuple{}} end (from)
      abstract type R <: Vector{Val{:el}} end
      struct Random.SamplerSimple{T, S, E} <: Random.Sampler{E} end (asdsa)
      struct R{v, a, *, vv, Tuple} <: Function end
    """;
    static String str = """
     function _tuplen(t::Type{<:Tuple}) in RAICode.QueryEva...uQQtils.jl:103 (method for generic function _tuplen)
     function f() @  asda/asds
     function ch(A::Stride{v } where v<:Union{  ComplexF64}, ::Type{LUp })
    """;

    static void parseArgs(String[] args) {
        if (args.length == 0) {
            args = defaultArgs;
        }
        for (var arg : args) {
            if (arg.equals("-d")) { // debug
                debug = true;
            } else if (arg.startsWith("-r ")) {  // root directory
                dir = arg.substring(3).strip();
            } else if (arg.startsWith("-t ")) {
                types = arg.substring(3).strip();
            } else if (arg.startsWith("-f ")) {
                functions = arg.substring(3).strip();
            } else if (arg.startsWith("-c ")) { // Color mode
                CodeColors.mode = switch (arg.substring(3).strip()) {
                    case "DARK" ->
                        CodeColors.Mode.DARK;
                    case "LIGHT" ->
                        CodeColors.Mode.LIGHT;
                    default ->
                        CodeColors.Mode.NONE;
                };
            } else {
                System.err.println("Unknown argument: " + arg);
                System.exit(1);
            }
        }
    }
    static String addType = """
        abstract type Any end
        abstract type Exception end
        abstract type Enum end
        abstract type Function end
        abstract type Tuple end
        abstract type Union end
        abstract type EnumX.Enum end
        abstract type UnionAll end
        abstract type DataType end
        abstract type Vector end
        abstract type AbstractVector end
        abstract type AbstractArray end
        abstract type AbstractVecOrMat end
        abstract type AbstractDict end
        """;
}
