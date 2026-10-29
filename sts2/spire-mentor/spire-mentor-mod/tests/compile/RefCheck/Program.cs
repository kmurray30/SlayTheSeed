// RefCheck <SpireMentor.dll> <sts2.dll> [--assembly sts2]
//
// Lists every reference SpireMentor.dll makes into the game assembly that the
// given sts2.dll cannot satisfy: a missing type, or a method / field whose
// name + signature (return type, parameter types, generic arity) is not
// declared on that type or a base class inside sts2. These are exactly the
// references that compile against one game build and then throw
// MissingMethodException / MissingFieldException / TypeLoadException at JIT
// time on another -- e.g. a member whose type went `uint` -> `ulong` still
// compiles from source (`var`) but no longer binds in IL.
//
// Exit: 0 all resolve, 1 unresolved references (listed), 2 usage / IO error.
// Pure metadata: neither assembly is loaded or executed.
//
// Not covered (by design -- see build_mod.sh): reflection-by-name strings and
// Harmony patch targets; those are audited against the decomp separately.

using System.Collections.Immutable;
using System.Reflection.Metadata;
using System.Reflection.Metadata.Ecma335;
using System.Reflection.PortableExecutable;

if (args.Length < 2)
{
    Console.Error.WriteLine("usage: RefCheck <SpireMentor.dll> <sts2.dll> [--assembly sts2]");
    return 2;
}
string gameAsm = "sts2";
for (int i = 2; i < args.Length; i++)
{
    if (args[i] == "--assembly" && i + 1 < args.Length) gameAsm = args[++i];
}

using var modPe = new PEReader(File.OpenRead(args[0]));
using var gamePe = new PEReader(File.OpenRead(args[1]));
MetadataReader mod = modPe.GetMetadataReader();
MetadataReader game = gamePe.GetMetadataReader();
var names = new NameProvider();

// ---- index the game assembly ------------------------------------------------
var typeDefs = new Dictionary<string, TypeDefinitionHandle>();
foreach (TypeDefinitionHandle h in game.TypeDefinitions)
{
    typeDefs[NameProvider.FullName(game, h)] = h;
}

string? BaseTypeName(TypeDefinitionHandle h)
{
    EntityHandle b = game.GetTypeDefinition(h).BaseType;
    if (b.IsNil) return null;
    return b.Kind switch
    {
        HandleKind.TypeDefinition => NameProvider.FullName(game, (TypeDefinitionHandle)b),
        HandleKind.TypeReference => NameProvider.FullName(game, (TypeReferenceHandle)b),
        HandleKind.TypeSpecification => NameProvider.GenericRoot(
            game.GetTypeSpecification((TypeSpecificationHandle)b).DecodeSignature(names, null)),
        _ => null,
    };
}

// Searches `type` and its sts2 base classes. Reaching a base outside sts2
// (System.Object, a Godot node) is a miss: Roslyn emits a member reference's
// parent as the type that DECLARES the member, so a reference parented on an
// sts2 type names a member sts2 itself must declare.
string Resolve(string type, string member, string sig, bool isField)
{
    string? t = type;
    while (t != null)
    {
        if (!typeDefs.TryGetValue(t, out TypeDefinitionHandle h))
        {
            return t == type ? "type missing" : "not found";
        }
        TypeDefinition td = game.GetTypeDefinition(h);
        if (isField)
        {
            foreach (FieldDefinitionHandle fh in td.GetFields())
            {
                FieldDefinition f = game.GetFieldDefinition(fh);
                if (game.GetString(f.Name) == member && f.DecodeSignature(names, null) == sig) return "found";
            }
        }
        else
        {
            foreach (MethodDefinitionHandle mh in td.GetMethods())
            {
                MethodDefinition m = game.GetMethodDefinition(mh);
                if (game.GetString(m.Name) == member && NameProvider.Sig(m.DecodeSignature(names, null)) == sig) return "found";
            }
        }
        t = BaseTypeName(h);
    }
    return "not found";
}

string? Candidates(string type, string member, bool isField)
{
    if (!typeDefs.TryGetValue(type, out TypeDefinitionHandle h)) return null;
    TypeDefinition td = game.GetTypeDefinition(h);
    var found = new List<string>();
    if (isField)
    {
        foreach (FieldDefinitionHandle fh in td.GetFields())
        {
            FieldDefinition f = game.GetFieldDefinition(fh);
            if (game.GetString(f.Name) == member) found.Add(f.DecodeSignature(names, null));
        }
    }
    else
    {
        foreach (MethodDefinitionHandle mh in td.GetMethods())
        {
            MethodDefinition m = game.GetMethodDefinition(mh);
            if (game.GetString(m.Name) == member) found.Add(NameProvider.Sig(m.DecodeSignature(names, null)));
        }
    }
    return found.Count == 0 ? null : string.Join(" | ", found);
}

// ---- walk the mod's references -----------------------------------------------
bool IsGameRef(TypeReferenceHandle h)
{
    TypeReference tr = mod.GetTypeReference(h);
    EntityHandle scope = tr.ResolutionScope;
    while (scope.Kind == HandleKind.TypeReference)
    {
        scope = mod.GetTypeReference((TypeReferenceHandle)scope).ResolutionScope;
    }
    return scope.Kind == HandleKind.AssemblyReference
        && mod.GetString(mod.GetAssemblyReference((AssemblyReferenceHandle)scope).Name) == gameAsm;
}

var gameTypeRefs = new HashSet<string>();
var problems = new SortedSet<string>(StringComparer.Ordinal);
int checkedTypes = 0, checkedMembers = 0;
foreach (TypeReferenceHandle h in mod.TypeReferences)
{
    if (!IsGameRef(h)) continue;
    string n = NameProvider.FullName(mod, h);
    gameTypeRefs.Add(n);
    checkedTypes++;
    if (!typeDefs.ContainsKey(n)) problems.Add($"TYPE   {n}: missing");
}

foreach (MemberReferenceHandle h in mod.MemberReferences)
{
    MemberReference mr = mod.GetMemberReference(h);
    string? parent = mr.Parent.Kind switch
    {
        HandleKind.TypeReference when IsGameRef((TypeReferenceHandle)mr.Parent)
            => NameProvider.FullName(mod, (TypeReferenceHandle)mr.Parent),
        HandleKind.TypeSpecification => NameProvider.GenericRoot(
            mod.GetTypeSpecification((TypeSpecificationHandle)mr.Parent).DecodeSignature(names, null)),
        _ => null,
    };
    if (parent == null || !gameTypeRefs.Contains(parent)) continue;
    string member = mod.GetString(mr.Name);
    bool isField = mr.GetKind() == MemberReferenceKind.Field;
    string sig = isField ? mr.DecodeFieldSignature(names, null) : NameProvider.Sig(mr.DecodeMethodSignature(names, null));
    checkedMembers++;
    string why = Resolve(parent, member, sig, isField);
    if (why != "found")
    {
        string? have = why == "not found" ? Candidates(parent, member, isField) : null;
        problems.Add($"{(isField ? "FIELD " : "METHOD")} {parent}::{member} {sig}: {why}"
            + (have != null ? $" (game has: {have})" : ""));
    }
}

Console.WriteLine($"RefCheck: {checkedTypes} {gameAsm} type refs, {checkedMembers} member refs checked against {args[1]}");
foreach (string p in problems) Console.WriteLine("  UNRESOLVED " + p);
Console.WriteLine(problems.Count == 0 ? "RefCheck: OK, every reference binds" : $"RefCheck: {problems.Count} unresolved reference(s)");
return problems.Count == 0 ? 0 : 1;

/// <summary>Renders signature types as stable, assembly-scope-free strings.</summary>
sealed class NameProvider : ISignatureTypeProvider<string, object?>
{
    public static string FullName(MetadataReader r, TypeDefinitionHandle h)
    {
        TypeDefinition td = r.GetTypeDefinition(h);
        string name = r.GetString(td.Name);
        TypeDefinitionHandle decl = td.GetDeclaringType();
        if (!decl.IsNil) return FullName(r, decl) + "/" + name;
        string ns = r.GetString(td.Namespace);
        return ns.Length == 0 ? name : ns + "." + name;
    }

    public static string FullName(MetadataReader r, TypeReferenceHandle h)
    {
        TypeReference tr = r.GetTypeReference(h);
        string name = r.GetString(tr.Name);
        if (tr.ResolutionScope.Kind == HandleKind.TypeReference)
        {
            return FullName(r, (TypeReferenceHandle)tr.ResolutionScope) + "/" + name;
        }
        string ns = r.GetString(tr.Namespace);
        return ns.Length == 0 ? name : ns + "." + name;
    }

    public static string GenericRoot(string s)
    {
        int i = s.IndexOf('<');
        return i < 0 ? s : s[..i];
    }

    public static string Sig(MethodSignature<string> m)
        => $"{m.ReturnType}({string.Join(",", m.ParameterTypes)})`{m.GenericParameterCount}{(m.Header.IsInstance ? "" : " static")}";

    public string GetPrimitiveType(PrimitiveTypeCode typeCode) => typeCode.ToString();
    public string GetTypeFromDefinition(MetadataReader reader, TypeDefinitionHandle handle, byte rawTypeKind) => FullName(reader, handle);
    public string GetTypeFromReference(MetadataReader reader, TypeReferenceHandle handle, byte rawTypeKind) => FullName(reader, handle);
    public string GetTypeFromSpecification(MetadataReader reader, object? genericContext, TypeSpecificationHandle handle, byte rawTypeKind)
        => reader.GetTypeSpecification(handle).DecodeSignature(this, genericContext);
    public string GetSZArrayType(string elementType) => elementType + "[]";
    public string GetArrayType(string elementType, ArrayShape shape) => elementType + "[" + new string(',', shape.Rank - 1) + "]";
    public string GetByReferenceType(string elementType) => elementType + "&";
    public string GetPointerType(string elementType) => elementType + "*";
    public string GetPinnedType(string elementType) => elementType;
    public string GetGenericInstantiation(string genericType, ImmutableArray<string> typeArguments)
        => genericType + "<" + string.Join(",", typeArguments) + ">";
    public string GetGenericMethodParameter(object? genericContext, int index) => "!!" + index;
    public string GetGenericTypeParameter(object? genericContext, int index) => "!" + index;
    public string GetFunctionPointerType(MethodSignature<string> signature) => "fnptr(" + Sig(signature) + ")";
    public string GetModifiedType(string modifier, string unmodifiedType, bool isRequired)
        => isRequired ? unmodifiedType + " modreq(" + modifier + ")" : unmodifiedType;
}
