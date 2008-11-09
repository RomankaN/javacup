package java_cup;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;

/** 
 * This class handles emitting generated code for the resulting parser.
 * The various parse tables must be constructed, etc. before calling any 
 * routines in this class.<p>  
 *
 * Three classes are produced by this code:
 *   <dl>
 *   <dt> symbol constant class
 *   <dd>   this contains constant declarations for each terminal (and 
 *          optionally each non-terminal).
 *   <dt> action class
 *   <dd>   this non-public class contains code to invoke all the user actions 
 *          that were embedded in the parser specification.
 *   <dt> parser class
 *   <dd>   the specialized parser class consisting primarily of some user 
 *          supplied general and initialization code, and the parse tables.
 *   </dl><p>
 *
 *  Three parse tables are created as part of the parser class:
 *    <dl>
 *    <dt> production table
 *    <dd>   lists the LHS non terminal number, and the length of the RHS of 
 *           each production.
 *    <dt> action table
 *    <dd>   for each state of the parse machine, gives the action to be taken
 *           (shift, reduce, or error) under each lookahead symbol.<br>
 *    <dt> reduce-goto table
 *    <dd>   when a reduce on a given production is taken, the parse stack is 
 *           popped back a number of elements corresponding to the RHS of the 
 *           production.  This reveals a prior state, which we transition out 
 *           of under the LHS non terminal symbol for the production (as if we
 *           had seen the LHS symbol rather than all the symbols matching the 
 *           RHS).  This table is indexed by non terminal numbers and indicates 
 *           how to make these transitions. 
 *    </dl><p>
 * 
 * In addition to the method interface, this class maintains a series of 
 * public global variables and flags indicating how misc. parts of the code 
 * and other output is to be produced, and counting things such as number of 
 * conflicts detected (see the source code and public variables below for
 * more details).<p> 
 *
 * This class is "static" (contains only data and methods).<p> 
 *
 * @see java_cup.main
 * @version last update: 11/25/95
 * @author Scott Hudson
 */

/* Major externally callable routines here include:
     symbols               - emit the symbol constant class 
     parser                - emit the parser class

   In addition the following major internal routines are provided:
     emit_package          - emit a package declaration
     emit_action_code      - emit the class containing the user's actions 
     emit_production_table - emit declaration and init for the production table
     do_action_table       - emit declaration and init for the action table
     do_reduce_table       - emit declaration and init for the reduce-goto table

   Finally, this class uses a number of public instance variables to communicate
   optional parameters and flags used to control how code is generated,
   as well as to report counts of various things (such as number of conflicts
   detected).  These include:

   prefix                  - a prefix string used to prefix names that would 
			     otherwise "pollute" someone else's name space.
   package_name            - name of the package emitted code is placed in 
			     (or null for an unnamed package.
   symbol_const_class_name - name of the class containing symbol constants.
   parser_class_name       - name of the class for the resulting parser.
   action_code             - user supplied declarations and other code to be 
			     placed in action class.
   parser_code             - user supplied declarations and other code to be 
			     placed in parser class.
   init_code               - user supplied code to be executed as the parser 
			     is being initialized.
   scan_code               - user supplied code to get the next Symbol.
   start_production        - the start production for the grammar.
   import_list             - list of imports for use with action class.
   num_conflicts           - number of conflicts detected. 
   nowarn                  - true if we are not to issue warning messages.
   not_reduced             - count of number of productions that never reduce.
   unused_term             - count of unused terminal symbols.
   unused_non_term         - count of unused non terminal symbols.
   *_time                  - a series of symbols indicating how long various
			     sub-parts of code generation took (used to produce
			     optional time reports in main).
*/

public class emit {

  /*-----------------------------------------------------------*/
  /*--- Variables ---------------------------------------------*/
  /*-----------------------------------------------------------*/

  /** The prefix placed on names that pollute someone else's name space. */
  public final static String prefix = "CUP$";

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Package that the resulting code goes into (null is used for unnamed). */
  public String package_name = null;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Name of the generated class for symbol constants. */
  public String symbol_const_class_name = "sym";

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Name of the generated parser class. */
  public String parser_class_name = "parser";

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

 /** TUM changes; proposed by Henning Niss 20050628: Type arguments for class declaration */
  public String class_type_argument = null;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** User declarations for direct inclusion in user action class. */
  public String action_code = null;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** User declarations for direct inclusion in parser class. */
  public String parser_code = null;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** User code for user_init() which is called during parser initialization. */
  public String init_code = null;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** User code for scan() which is called to get the next Symbol. */
  public String scan_code = null;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** The start production of the grammar. */
  public production start_production = null;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** List of imports (Strings containing class names) to go with actions. */
  public ArrayList<String> import_list = new ArrayList<String>();

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Do we skip warnings? */
  public boolean nowarn = false;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Count of the number on non-reduced productions found. */
  public int not_reduced = 0;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Count of unused terminals. */
  public int unused_term = 0;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Count of unused non terminals. */
  public int unused_non_term = 0;

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /* Timing values used to produce timing report in main.*/

  /** Time to produce symbol constant class. */
  public long symbols_time          = 0;

  /** Time to produce parser class. */
  public long parser_time           = 0;

  /** Time to produce action code class. */
  public long action_code_time      = 0;

  /** Time to produce the production table. */
  public long production_table_time = 0;

  /** Time to produce the action table. */
  public long action_table_time     = 0;

  /** Time to produce the reduce-goto table. */
  public long goto_table_time       = 0;

  /*-----------------------------------------------------------*/
  /*--- General Methods ---------------------------------------*/
  /*-----------------------------------------------------------*/

  /** Build a string with the standard prefix. 
   * @param str string to prefix.
   */
  protected String pre(String str) {
    return prefix + str;
  }

   /**
    * TUM changes; proposed by Henning Niss 20050628 
    * Build a string with the specified type arguments,
    * if present, otherwise an empty string.
    */
   protected String typeArgument() {
     return class_type_argument == null ? "" : "<" + class_type_argument + ">";
   }

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Emit a package spec if the user wants one. 
   * @param out stream to produce output on.
   */
  protected void emit_package(PrintWriter out)
    {
      /* generate a package spec if we have a name for one */
      if (package_name != null) {
	out.println("package " + package_name + ";"); out.println();
      }
    }

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/
  
  public String stackelem(int index, boolean is_java15)
    {
      String access;
      if (index == 1)
	access = pre("stack") + ".peek()";
      else
	access = pre("stack") + ".elementAt(" + pre("size") + "-" + index + ")";
      return is_java15 ? access : "((java_cup.runtime.Symbol) "+access+")";
    }

  /** Emit code for the symbol constant class, optionally including non terms,
   *  if they have been requested.  
   * @param out            stream to produce output on.
   * @param emit_non_terms do we emit constants for non terminals?
   * @param sym_interface  should we emit an interface, rather than a class?
   */
  public void symbols(PrintWriter out, 
			     boolean emit_non_terms, boolean sym_interface)
    {
      String class_or_interface = (sym_interface)?"interface":"class";

      long start_time = System.currentTimeMillis();

      /* top of file */
      out.println();
      out.println("//----------------------------------------------------"); 
      out.println("// The following code was generated by " + 
							   version.title_str);
      out.println("// " + new Date());
      out.println("//----------------------------------------------------"); 
      out.println();
      emit_package(out);

      /* class header */
      out.println("/** CUP generated " + class_or_interface + 
		  " containing symbol constants. */");
      out.println("public " + class_or_interface + " " + 
		  symbol_const_class_name + " {");

      out.println("  /* terminals */");

      /* walk over the terminals */              /* later might sort these */
      for (terminal term : terminal.all())
	{

	  /* output a constant decl for the terminal */
	  out.println("  public static final int " + term.name() + " = " + 
		      term.index() + ";");
	}

      /* do the non terminals if they want them (parser doesn't need them) */
      if (emit_non_terms)
	{
          out.println();
          out.println("  /* non terminals */");

          /* walk over the non terminals */       /* later might sort these */
          for (non_terminal nt : non_terminal.all())
	    {
          // ****
          // TUM Comment: here we could add a typesafe enumeration
          // ****

	      /* output a constant decl for the terminal */
	      out.println("  static final int " + nt.name() + " = " + 
		          nt.index() + ";");
	    }
	}

      /* end of class */
      out.println("}");
      out.println();

      symbols_time = System.currentTimeMillis() - start_time;
    }

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Emit code for the non-public class holding the actual action code. 
   * @param out        stream to produce output on.
   * @param start_prod the start production of the grammar.
   */
  protected void emit_action_code(PrintWriter out, production start_prod, 
      boolean lr_values, boolean is_java15)
    {
      long start_time = System.currentTimeMillis();

      /* Stack generic parameter and optional casts depending on Java Version */
      String genericArg = is_java15 ? "<java_cup.runtime.Symbol>" : "           ";

      /* class header */
      out.println();
      out.println(
       "/** Cup generated class to encapsulate user supplied action code.*/"
      );  
      /* TUM changes; proposed by Henning Niss 20050628: added type arguement */
      out.println("class " +  pre("actions") + typeArgument() + " {");
      /* user supplied code */
      if (action_code != null)
	{
	  out.println();
          out.println(action_code);
	}

      /* field for parser object */
      /* TUM changes; proposed by Henning Niss 20050628: added typeArgument */
      out.println("  private final "+parser_class_name + typeArgument() + " parser;");

      /* constructor */
      out.println();
      out.println("  /** Constructor */");
      /* TUM changes; proposed by Henning Niss 20050628: added typeArgument */
      out.println("  " + pre("actions") + "("+parser_class_name+typeArgument()+" parser) {");
      out.println("    this.parser = parser;");
      out.println("  }");

      /* action method head */
      out.println();
      out.println("  /** Method with the actual generated action code. */");
      if (is_java15)
	out.println("  @SuppressWarnings({ \"unused\", \"unchecked\" })");
      out.println("  public final java_cup.runtime.Symbol " + 
		     pre("do_action") + "(");
      out.println("    int                        " + pre("act_num,"));
      out.println("    java_cup.runtime.LRParser  " + pre("parser,"));
      out.println("    java.util.Stack"+genericArg+" " + pre("stack)"));
      out.println("    throws java.lang.Exception");
      out.println("    {");

      out.println("      /* Stack size for peeking into the stack */");
      out.println("      int " + pre("size") + " = "+pre("stack")+".size();");
      out.println();
      out.println("      /* Symbol object for return from actions */");
      out.println("      java_cup.runtime.Symbol " + pre("result") + ";");
      out.println();

      /* switch top */
      out.println("      /* select the action based on the action number */");
      out.println("      switch (" + pre("act_num") + ")");
      out.println("        {");

      /* emit action code for each production as a separate case */
      for (production prod : production.all())
	{
	  /* case label */
          out.println("          /*. . . . . . . . . . . . . . . . . . . .*/");
          out.println("          case " + prod.index() + ": // " + 
					  prod.toString());

	  /* give them their own block to work in */
	  out.println("            {");

	  if (prod.lhs().the_symbol().stack_type() != null)
	    {
	      int lastResult = prod.getIndexOfIntermediateResult();
	      String result = "null";
	      if (lastResult!=-1)
		{
		  result =  "(" + prod.lhs().the_symbol().stack_type() + ") " +
		  stackelem(prod.rhs_params() - lastResult, is_java15)+".value";
		}

	      /* create the result symbol */
	      /* make the variable RESULT which will point to the new Symbol 
	       * (see below) and be changed by action code
	       * 6/13/96 frankf */
	      out.println("              " +  prod.lhs().the_symbol().stack_type() +
		  " RESULT ="+result+";");
	    }
	  production baseprod;
	  if (prod instanceof action_production)
	    baseprod = ((action_production)prod).base_production();
	  else
	    baseprod = prod;
	  /* Add code to propagate RESULT assignments that occur in
	   * action code embedded in a production (ie, non-rightmost
	   * action code). 24-Mar-1998 CSA
	   */
	  for (int i=prod.rhs_params()-1; i>=0; i--) 
	    {
	      String symbvar = null;
	      if (!(prod instanceof action_production) &&
		  lr_values && (i == 0 || i == prod.rhs_params()-1))
		{
		  if (i == prod.rhs_params()-1)
		    symbvar = pre("right");
		  else
		    symbvar = pre("left");

		  out.println("              java_cup.runtime.Symbol " + symbvar + " = " +
		      stackelem(prod.rhs_params() - i, is_java15) + ";");
		}
	      
	      symbol_part symbol = baseprod.rhs(i);
	      if (symbol.label() != null)
		{
		  if (symbvar == null)
		    {
		      symbvar = pre("sym"+symbol.label());
		      out.println("              java_cup.runtime.Symbol " + symbvar + " = " +
			  stackelem(prod.rhs_params() - i, is_java15) + ";");
		    }
		  /* Put in the left/right value labels */
		  if (lr_values)
		    {
		      out.println("              int "+symbol.label()+"left = "+
			  symbvar + ".left;");
		      out.println("              int "+symbol.label()+"right = "+
			  symbvar + ".right;");
		    }

		  String symtype = symbol.the_symbol().stack_type(); 
		  if (symtype != null)
		    {
		      out.println("              " + symtype +
			  " " + symbol.label() + " = (" + symtype + ") " +
			  symbvar + ".value;");
		    }
		}
	    }

	  /* if there is an action string, emit it */
          if (prod.action() != null)
            out.println(prod.action().code_string());

	  /* here we have the left and right values being propagated.  
		must make this a command line option.
	     frankf 6/18/96 */

         /* Create the code that assigns the left and right values of
            the new Symbol that the production is reducing to */
          String leftright = "";
	  if (lr_values)
	    {
	      String leftsym = pre("left");
	      String rightsym = pre("right");
	      if (prod.rhs_length() == 0)
		{
		  out.println("              java_cup.runtime.Symbol " + rightsym + " = " +
		      stackelem(1, is_java15) + ";");
		}
	      if (prod.rhs_length() < 2)
		leftsym = rightsym;
	      leftright = ", " + leftsym + ", " + rightsym;
	    }
	  String result = prod.lhs().the_symbol().stack_type() != null 
	  	? ", RESULT" : "";

	  out.println("              " + pre("result") + " = parser.getSymbolFactory().newSymbol(" + 
	      "\""+ 	prod.lhs().the_symbol().name() +  "\","+ 
	      prod.lhs().the_symbol().index() + leftright + result + ");");
	  
	  /* end of their block */
	  out.println("            }");

	  /* if this was the start production, do action for accept */
	  if (prod == start_prod)
	    {
	      out.println("          /* ACCEPT */");
	      out.println("          " + pre("parser") + ".done_parsing();");
	    }

	  /* code to return lhs symbol */
	  out.println("          return " + pre("result") + ";");
	  out.println();
	}

      /* end of switch */
      out.println("          /* . . . . . .*/");
      out.println("          default:");
      out.println("            throw new Exception(");
      out.println("               \"Invalid action number found in " +
				  "internal parse table\");");
      out.println();
      out.println("        }");

      /* end of method */
      out.println("    }");

      /* end of class */
      out.println("}");
      out.println();

      action_code_time = System.currentTimeMillis() - start_time;
    }

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Emit the production table. 
   * @param out stream to produce output on.
   */
  protected String do_production_table()
    {
      long start_time = System.currentTimeMillis();

      // make short[][]
      short[] prod_table = new short[2*production.number()];
      for (production prod : production.all())
	{
	  // { lhs symbol , rhs size }
	  prod_table[2*prod.index()+0] = (short) prod.lhs().the_symbol().index();
	  prod_table[2*prod.index()+1] = (short) prod.rhs_length();
	}
      String result = do_array_as_string(prod_table);
      production_table_time = System.currentTimeMillis() - start_time;
      return result;
    }

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Emit the action table. 
   * @param out             stream to produce output on.
   * @param act_tab         the internal representation of the action table.
   * @param compact_reduces do we use the most frequent reduce as default?
   */
  private String do_action_table(
    parse_action_table act_tab,
    boolean            compact_reduces)
    {
      long start_time = System.currentTimeMillis();
      String result = do_array_as_string(act_tab.compress(compact_reduces));
      action_table_time = System.currentTimeMillis() - start_time;
      return result;
    }

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Create the compressed reduce-goto table. 
   * @param red_tab the internal representation of the reduce-goto table.
   */
  private String do_reduce_table(parse_reduce_table red_tab)
    {
      long start_time = System.currentTimeMillis();
      String result = do_array_as_string(red_tab.compress());
      goto_table_time = System.currentTimeMillis() - start_time;
      return result;
    }

  /** create a string encoding a given short[] array.*/
  private String do_array_as_string(short[] sa) 
    {
      short min_value = 0;
      for (int i = 0; i < sa.length; i++)
	if (sa[i] < min_value)
	  min_value = sa[i];
      StringBuilder sb = new StringBuilder();
      sb.append((char)(sa.length >>> 16)).append((char)(sa.length & 0xffff));
      sb.append((char)-min_value);
      for (int i = 0; i < sa.length; i++)
	sb.append((char) (sa[i]-min_value));
      return sb.toString();
    }
    // print a string array encoding the given short[][] array.
  protected void output_string(PrintWriter out, String str) {
    for (int i = 0; i < str.length(); i += 11)
      {
	StringBuilder encoded = new StringBuilder();
	encoded.append("    \"");
	for (int j = 0; j < 11 && i+j < str.length(); j++)
	  {
	    char c = str.charAt(i+j);
	    encoded.append('\\');
	    if (c < 256) 
	      {
		String oct = "000"+Integer.toOctalString(c);
		oct = oct.substring(oct.length()-3);
		encoded.append(oct);
	      }
	    else
	      {
		String hex = "0000"+Integer.toHexString(c);
		hex = hex.substring(hex.length()-4);
		encoded.append('u').append(hex);
	      }
	  }
	encoded.append("\"");
	if (i+11 < str.length())
	  encoded.append(" +");
	else
	  encoded.append(";");
	out.println(encoded.toString());
      }
  }

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Emit the parser subclass with embedded tables. 
   * @param out             stream to produce output on.
   * @param action_table    internal representation of the action table.
   * @param reduce_table    internal representation of the reduce-goto table.
   * @param start_st        start state of the parse machine.
   * @param start_prod      start production of the grammar.
   * @param compact_reduces do we use most frequent reduce as default?
   * @param suppress_scanner should scanner be suppressed for compatibility?
   */
  public void parser(
    PrintWriter        out, 
    parse_action_table action_table,
    parse_reduce_table reduce_table,
    int                start_st,
    production         start_prod,
    boolean            compact_reduces,
    boolean            suppress_scanner,
    boolean            lr_values,
    boolean            is_java15)
    {
      long start_time = System.currentTimeMillis();

      /* top of file */
      out.println();
      out.println("//----------------------------------------------------"); 
      out.println("// The following code was generated by " + 
							version.title_str);
      out.println("// " + new Date());
      out.println("//----------------------------------------------------"); 
      out.println();
      emit_package(out);

      /* user supplied imports */
      for (String imp : import_list)
	out.println("import " + imp + ";");

      /* class header */
      out.println();
      out.println("/** "+version.title_str+" generated parser.");
      out.println("  * @version " + new Date());
      out.println("  */");
      /* TUM changes; proposed by Henning Niss 20050628: added typeArgument */
      out.println("public class " + parser_class_name + typeArgument() +
		  " extends java_cup.runtime.LRParser {");

      /* constructors [CSA/davidm, 24-jul-99] */
      out.println();
      out.println("  /** Default constructor. */");
      out.println("  public " + parser_class_name + "() {super();}");
      if (!suppress_scanner) {
	  out.println();
	  out.println("  /** Constructor which sets the default scanner. */");
	  out.println("  public " + parser_class_name + 
		      "(java_cup.runtime.Scanner s) {super(s);}");
          // TUM 20060327 added SymbolFactory aware constructor
	  out.println();
	  out.println("  /** Constructor which sets the default scanner. */");
	  out.println("  public " + parser_class_name + 
		      "(java_cup.runtime.Scanner s, java_cup.runtime.SymbolFactory sf) {super(s,sf);}");
      }

      /* emit the various tables */
      String tables = do_production_table() + 
      	do_action_table(action_table, compact_reduces) +
      	do_reduce_table(reduce_table);

      /* instance of the action encapsulation class */
      out.println("  /** Return action table */");
      out.println("  protected String action_table() { ");
      out.println("    return");
      output_string(out, tables);
      out.println("  }");
      out.println();

      /* instance of the action encapsulation class */
      out.println("  /** Instance of action encapsulation class. */");
      out.println("  protected " + pre("actions") + " action_obj;");
      out.println();

      /* action object initializer */
      out.println("  /** Action encapsulation object initializer. */");
      out.println("  protected void init_actions()");
      out.println("    {");
      /* TUM changes; proposed by Henning Niss 20050628: added typeArgument */
      out.println("      action_obj = new " + pre("actions") + typeArgument() +"(this);");
      out.println("    }");
      out.println();

      /* access to action code */
      out.println("  /** Invoke a user supplied parse action. */");
      out.println("  public java_cup.runtime.Symbol do_action(");
      out.println("    int                        act_num,");
      if (is_java15)
	out.println("    java.util.Stack<java_cup.runtime.Symbol> stack)");
      else
	out.println("    java.util.Stack            stack)");
      out.println("    throws java.lang.Exception");
      out.println("  {");
      out.println("    /* call code in generated class */");
      out.println("    return action_obj." + pre("do_action(") +
                  "act_num, this, stack);");
      out.println("  }");
      out.println("");


      /* method to tell the parser about the start state */
      out.println("  /** Indicates start state. */");
      out.println("  public int start_state() {return " + start_st + ";}");

      /* method to indicate start production */
      out.println("  /** Indicates start production. */");
      out.println("  public int start_production() {return " + 
		     start_production.index() + ";}");
      out.println();

      /* methods to indicate EOF and error symbol indexes */
      out.println("  /** <code>EOF</code> Symbol index. */");
      out.println("  public int EOF_sym() {return " + terminal.EOF.index() + 
					  ";}");
      out.println();
      out.println("  /** <code>error</code> Symbol index. */");
      out.println("  public int error_sym() {return " + terminal.error.index() +
					  ";}");
      out.println();

      /* user supplied code for user_init() */
      if (init_code != null)
	{
          out.println();
	  out.println("  /** User initialization code. */");
	  out.println("  public void user_init() throws java.lang.Exception");
	  out.println("    {");
	  out.println(init_code);
	  out.println("    }");
	}

      /* user supplied code for scan */
      if (scan_code != null)
	{
          out.println();
	  out.println("  /** Scan to get the next Symbol. */");
	  out.println("  public java_cup.runtime.Symbol scan()");
	  out.println("    throws java.lang.Exception");
	  out.println("    {");
	  out.println(scan_code);
	  out.println("    }");
	}

      /* user supplied code */
      if (parser_code != null)
	{
	  out.println();
          out.println(parser_code);
	}

      /* end of class */
      out.println("}");

      /* put out the action code class */
      emit_action_code(out, start_prod, lr_values, is_java15);

      parser_time = System.currentTimeMillis() - start_time;
    }

    /*-----------------------------------------------------------*/
}
