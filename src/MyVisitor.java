import org.antlr.v4.runtime.tree.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Created by fengjiang on 2/19/18.
 */
public class MyVisitor extends XQueryBaseVisitor{

    private List<Node> contextNodes = new ArrayList<>();
    private Map<String, Object> vars = new HashMap<>();
    private Document doc;

    @Override
    public Object visitQuery(XQueryParser.QueryContext ctx) {
        return this.visit(ctx.xq());
    }

    @Override
    public Object visitXq_xq(XQueryParser.Xq_xqContext ctx) {
        return this.visit(ctx.xq());
    }

    @Override
    public Object visitXq_makeElement(XQueryParser.Xq_makeElementContext ctx) {
        String tagName = ctx.tagName(0).getText();
        if (!tagName.equals(ctx.tagName(1).getText().replace("/", ""))) {
            throw new RuntimeException("Invalid tag name");
        }
        if (this.doc == null) {
            initDoc();
        }
        List<Node> res = Helper.asListNode(this.visit(ctx.xq()));
        List<Node> tempRes = new ArrayList<>();
        Node node = this.doc.createElement(tagName);
        for (Node n: res) {
            Node tempNode = this.doc.importNode(n, true);
            node.appendChild(tempNode);
        }
        tempRes.add(node);
        return tempRes;
    }

    @Override
    public Object visitXq_concat(XQueryParser.Xq_concatContext ctx) {
        List<Node> prevContextNodes = new ArrayList<>();
        prevContextNodes.addAll(contextNodes);
        List<Node> l1 = (List<Node>) this.visit(ctx.xq(0));
        contextNodes = prevContextNodes;
        List<Node> l2 = (List<Node>) this.visit(ctx.xq(1));
        Set<Node> res = new HashSet<>();
        res.addAll(l1);
        res.addAll(l2);
        return new ArrayList<>(res);
    }



    @Override
    public Object visitXq_ap(XQueryParser.Xq_apContext ctx) {
        return this.visit(ctx.ap());
    }

    @Override
    public Object visitXq_nextLevelRecursive(XQueryParser.Xq_nextLevelRecursiveContext ctx) {
        contextNodes = getChildrenRecursive(Helper.asListNode(this.visit(ctx.xq())));
        return this.visit(ctx.rp());
    }

    @Override
    public Object visitXq_var(XQueryParser.Xq_varContext ctx) {
        return this.visit(ctx.var());
    }

    @Override
    public Object visitXq_join(XQueryParser.Xq_joinContext ctx) {
        return this.visit(ctx.joinClause());
    }

    @Override
    public Object visitXq_makeText(XQueryParser.Xq_makeTextContext ctx) {
        return this.visit(ctx.strConstant());
    }

    @Override
    public Object visitXq_let(XQueryParser.Xq_letContext ctx) {
        return this.visit(ctx.letClause());
    }

    @Override
    public Object visitXq_nextLevel(XQueryParser.Xq_nextLevelContext ctx) {
        contextNodes = getChildren(Helper.asListNode(this.visit(ctx.xq())));
        return this.visit(ctx.rp());
    }

    @Override
    public Object visitVar(XQueryParser.VarContext ctx) {
        return this.vars.get(ctx.getText());
    }

    @Override
    public Object visitStrConstant(XQueryParser.StrConstantContext ctx) {
        List<Node> res = new ArrayList<>();
        if (this.doc == null)
            initDoc();
        res.add(this.doc.createTextNode(ctx.getText().replace("\"", "")));
        return res;
    }

    //TODO:
    @Override
    public Object visitForClause(XQueryParser.ForClauseContext ctx) {
        return super.visitForClause(ctx);
    }

    @Override
    public Object visitXq_loop(XQueryParser.Xq_loopContext ctx) {
        Map<String, Object> oldVars = new HashMap<>(this.vars);
        List<Node> res = new ArrayList<>();
        this.visitFor(res, ctx, 0);
        this.vars = oldVars;
        return res;
    }

    private void visitFor(List<Node> res, XQueryParser.Xq_loopContext ctx, int idx) {
        if (idx >= ctx.forClause().var().size()) {
            if (ctx.letClause() != null)
                this.visit(ctx.letClause());
            if (ctx.whereClause() != null)
                if (!((boolean) this.visit(ctx.whereClause())))
                    return;
            List<Node> temp = (List<Node>) this.visit(ctx.returnClause());
            res.addAll(temp);
            return;
        }
        String key = ctx.forClause().var(idx).getText();
        List<Node> values = (List<Node>) this.visit(ctx.forClause().xq(idx));
        for (Node node: values) {
            List<Node> temp = new ArrayList<>();
            temp.add(node);
            this.vars.put(key, temp);
            visitFor(res, ctx, idx+1);
        }
    }

    @Override
    public Object visitLetClause(XQueryParser.LetClauseContext ctx) {
        for (int i = 0; i < ctx.var().size(); ++i)
            this.vars.put(ctx.var(i).getText(), this.visit(ctx.xq(i)));
        return null;
    }

    @Override
    public Object visitWhereClause(XQueryParser.WhereClauseContext ctx) {
        return this.visit(ctx.cond());
    }

    @Override
    public Object visitReturnClause(XQueryParser.ReturnClauseContext ctx) {
        return this.visit(ctx.xq());
    }

    @Override
    public Object visitJoinClause(XQueryParser.JoinClauseContext ctx) {
        // make a copy of the context nodes
        List<Node> prevContextNodes = new ArrayList<>();
        prevContextNodes.addAll(contextNodes);
        Set<Node> leftTuples = new HashSet<>();
        leftTuples.addAll(Helper.asListNode(this.visit(ctx.xq(0))));
        contextNodes = prevContextNodes;
        Set<Node> rightTuples = new HashSet<>();
        rightTuples.addAll(Helper.asListNode(this.visit(ctx.xq(1))));
        List<String> leftAttris = new ArrayList<>();
        for (TerminalNode idstring : ctx.listOfConst(0).IDSTRING()) {
            leftAttris.add(idstring.getText());
        }
        List<String> rightAttris = new ArrayList<>();
        for (TerminalNode idstring : ctx.listOfConst(1).IDSTRING()) {
            rightAttris.add(idstring.getText());
        }

        Map<KeyWrapper, ArrayList<Node>> hashMap = new HashMap<>();
        for(Node tuple : leftTuples) {
            // each tuple
            NodeList tupleElements = tuple.getChildNodes();
            KeyWrapper key = new KeyWrapper();
            // create key with the specified attribute
            for (int i = 0; i < leftAttris.size(); i++) {
                String attri = leftAttris.get(i);
                for (int j = 0; j < tupleElements.getLength(); j++) {
                    Node curElement = tupleElements.item(j);
                    if (curElement.getNodeType() == Node.ELEMENT_NODE && curElement.getNodeName().equals(attri)){
                        key.keyNodes.add(curElement);
                        break;
                    }
                }
            }
            if (!hashMap.containsKey(key))
                hashMap.put(key, new ArrayList<>());
            hashMap.get(key).add(tuple);
        }

        List<Node> res = new ArrayList<>();
        for(Node tuple : rightTuples) {
            // each tuple
            NodeList tupleElements = tuple.getChildNodes();
            KeyWrapper key = new KeyWrapper();
            // create key with the specified attribute
            for (int i = 0; i < rightAttris.size(); i++) {
                String attri = rightAttris.get(i);
                for (int j = 0; j < tupleElements.getLength(); j++) {
                    Node curElement = tupleElements.item(j);
                    if (curElement.getNodeType() == Node.ELEMENT_NODE && curElement.getNodeName().equals(attri)){
                        key.keyNodes.add(curElement);
                        break;
                    }
                }
            }

            if (hashMap.containsKey(key)) {
                for (Node leftTuple : hashMap.get(key)) {
                    for (int j = 0; j < tupleElements.getLength(); j++) {
                        Node curElement = tupleElements.item(j);
                        Node newNode = leftTuple.getOwnerDocument().importNode(curElement, true);
                        leftTuple.appendChild(newNode);
                    }
                    res.add(leftTuple);
                }
            }

        }

        contextNodes = res;

        return res;
    }

    @Override
    public Object visitListOfConst(XQueryParser.ListOfConstContext ctx) {
        return null;
    }

    // definition: [[Cond1 and Cond2]]C (C) = [[Cond1]]C (C) ∧ [[Cond2]]C (C)
    @Override
    public Object visitCond_and(XQueryParser.Cond_andContext ctx) {
        // make a copy of the context nodes
        List<Node> prevContextNodes = new ArrayList<>();
        prevContextNodes.addAll(contextNodes);
        boolean leftCond = (boolean)this.visit(ctx.cond(0));
        contextNodes = prevContextNodes;
        boolean rightCond = (boolean)this.visit(ctx.cond(1));
        return leftCond && rightCond;
    }

    // definition: [[empty(XQ1)]]C (C) = [[XQ1]]X(C) =<>
    @Override
    public Object visitCond_empty(XQueryParser.Cond_emptyContext ctx) {
        Object result = this.visit(ctx.xq());
        return Helper.isListNode(result) && ((List<Node>)result).size() == 0;
    }

    // definition: [[rp1 = rp2]]F (n) = [[rp1 eq rp2]]F (n) = ∃x ∈ [[rp1]]R(n) ∃y ∈ [[rp2]]R(n) x eq y
    @Override
    public Object visitCond_equal(XQueryParser.Cond_equalContext ctx) {
        // make a copy of the context nodes
        List<Node> prevContextNodes = new ArrayList<>();
        prevContextNodes.addAll(contextNodes);
        List<Node> leftXq = Helper.asListNode(this.visit(ctx.xq(0)));
        contextNodes = prevContextNodes;
        List<Node> rightXq = Helper.asListNode(this.visit(ctx.xq(1)));
        return checkEq(leftXq, rightXq);
    }

    // definition: [[XQ1 is XQ2]]C (C) = [[XQ1 == XQ2]]C (C) = ∃x ∈ [[XQ1]]X(C) ∃y ∈ [[XQ2]]X(C) x is y
    @Override
    public Object visitCond_is(XQueryParser.Cond_isContext ctx) {
        // make a copy of the context nodes
        List<Node> prevContextNodes = new ArrayList<>();
        prevContextNodes.addAll(contextNodes);
        List<Node> leftXq = Helper.asListNode(this.visit(ctx.xq(0)));
        contextNodes = prevContextNodes;
        List<Node> rightXq = Helper.asListNode(this.visit(ctx.xq(1)));
        return checkIs(leftXq, rightXq);
    }

    // definition: [[(Cond1)]]C (C) = [[Cond1]]C (C)
    @Override
    public Object visitCond_cond(XQueryParser.Cond_condContext ctx) {
        return this.visit(ctx.cond());
    }


    // definition:
    @Override
    public Object visitCond_some(XQueryParser.Cond_someContext ctx) {
        Map<String, Object> oldVars = new HashMap<>(this.vars);
        List<Node> nodes = new ArrayList<>();
        boolean res = this.visitSome(ctx, 0);
        this.vars = oldVars;
        return res;
    }

    private boolean visitSome(XQueryParser.Cond_someContext ctx, int idx) {
        if (idx >= ctx.xq().size()) {
            return (boolean) this.visit(ctx.cond());
        }
        String var = ctx.var(idx).getText();
        List<Node> nodes = (List<Node>) this.visit(ctx.xq(idx));
        for (Node node: nodes) {
            List<Node> temp = new ArrayList<>();
            temp.add(node);
            this.vars.put(var, temp);
            boolean res = visitSome(ctx, idx+1);
            if (res)
                return true;
        }
        return false;
    }

    // definition: [[not Cond1]]C (C) = ¬[[Cond1]]C (C)
    @Override
    public Object visitCond_not(XQueryParser.Cond_notContext ctx) {
        return !((boolean)this.visit(ctx.cond()));
    }

    // definition: [[Cond1 or Cond2]]C (C) = [[Cond1]]C (C) ∨ [[Cond2]]C (C)
    @Override
    public Object visitCond_or(XQueryParser.Cond_orContext ctx) {
        // make a copy of the context nodes
        List<Node> prevContextNodes = new ArrayList<>();
        prevContextNodes.addAll(contextNodes);
        boolean leftCond = (boolean)this.visit(ctx.cond(0));
        contextNodes = prevContextNodes;
        boolean rightCond = (boolean)this.visit(ctx.cond(1));
        return leftCond || rightCond;
    }

    @Override
    public List<Node> visitAp(XQueryParser.ApContext ctx) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        String fileName = ctx.fileName().getText().replace('\"', ' ').trim();
        List<Node> childNodes;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();

            //parse using builder to get DOM representation of the XML file
            List<Node> prev = new ArrayList<>();

            this.doc = db.parse(fileName);
            prev.add(this.doc);

            //Visit children
            if (ctx.getChild(5).getText().equals("/")) {
                childNodes = getChildrenRecursive(prev);
            } else {
                childNodes = getChildren(prev);
            }
            contextNodes = childNodes;
            XQueryParser.RpContext rpContext = ctx.rp();
            this.visit(rpContext);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return contextNodes;
    }

    // definition: [[@attName]]R(n) = attrib(n, attName)
    @Override
    public List<Node> visitRp_attName(XQueryParser.Rp_attNameContext ctx) {
        List<Node> next = new ArrayList<>();
        String attrName = ctx.getText();
        for (Node node : contextNodes) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            Attr attrNode = element.getAttributeNode(attrName);
            next.add(attrNode);
        }
        contextNodes = next;
        return contextNodes;
    }

    // definition: [[tagName]]R(n) = < c | c ← [[∗]]R(n),tag(c) = tagName >
    @Override
    public List<Node> visitRp_tagNmae(XQueryParser.Rp_tagNmaeContext ctx) {
        List<Node> prev = contextNodes;
        List<Node> next = new ArrayList<>();
        for (Node node : prev) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (element.getTagName().equals(ctx.getText())) {
                    next.add(element);
                }
            }

        }
        contextNodes = next;
        return contextNodes;
    }

    // definition: [[..]]R(n) = parent(n)
    @Override
    public List<Node> visitRp_parent(XQueryParser.Rp_parentContext ctx) {
        Set<Node> nextSet = new HashSet<>();
        for (Node node : contextNodes) {
            try {
                nextSet.add(node.getParentNode().getParentNode());
            } catch (Exception e) {
                nextSet.add(node.getOwnerDocument());
            }
        }
        contextNodes = new ArrayList<>(nextSet);
        return contextNodes;
    }

    // definition: [[(rp)]]R(n) = [[rp]]R(n)
    @Override
    public List<Node> visitRp_rp(XQueryParser.Rp_rpContext ctx) {
        contextNodes = Helper.asListNode(this.visit(ctx.rp()));
        return contextNodes;
    }

    // definition: [rp1/rp2]]R(n) = unique(< y | x ← [[rp1]]R(n), y ← [[rp2]]R(x) >)
    @Override
    public List<Node> visitRp_nextLevel(XQueryParser.Rp_nextLevelContext ctx) {
        List<Node> rp1 = Helper.asListNode(this.visit(ctx.rp(0)));
        List<Node> rp1ChildNodes = getChildren(rp1);
        contextNodes = rp1ChildNodes;
        this.visit(ctx.rp(1));
        return contextNodes;
    }

    // definition: [[rp1//rp2]]R(n) = unique([[rp1/rp2]]R(n), [[rp1/ ∗ //rp2]]R(n))
    @Override
    public List<Node> visitRp_nextLevelRecursive(XQueryParser.Rp_nextLevelRecursiveContext ctx) {
        List<Node> rp1 = Helper.asListNode(this.visit(ctx.rp(0)));
        List<Node> rp1ChildNodes = getChildrenRecursive(rp1);
        contextNodes = rp1ChildNodes;
        this.visit(ctx.rp(1));
        return contextNodes;
    }

    // definition: [[∗]]R(n) = children(n)
    @Override
    public List<Node> visitRp_children(XQueryParser.Rp_childrenContext ctx) {
        return contextNodes;
    }

    // definition: [[.]]R(n) = < n >
    @Override
    public List<Node> visitRp_current(XQueryParser.Rp_currentContext ctx) {
        Set<Node> nextSet = new HashSet<>();
        for (Node node : contextNodes) {
            try {
                nextSet.add(node.getParentNode());
            } catch (Exception e) {
                nextSet.add(node.getOwnerDocument());
            }
        }
        contextNodes = new ArrayList<>(nextSet);
        return contextNodes;
    }

    // definition: [[text()]]R(n) = txt(n)
    @Override
    public List<Node> visitRp_text(XQueryParser.Rp_textContext ctx) {
        List<Node> next = new ArrayList<>();
        for (Node node : contextNodes) {
            if (node.getNodeType() == Node.TEXT_NODE) {
                next.add(node);
            }
        }
        contextNodes = next;
        return contextNodes;
    }

    // definition: [[rp1, rp2]]R(n) = [[rp1]]R(n), [[rp2]]R(n)
    @Override
    public List<Node> visitRp_concat(XQueryParser.Rp_concatContext ctx) {
        // make a copy of the context nodes
        List<Node> prevContextNodes = new ArrayList<>();
        prevContextNodes.addAll(contextNodes);

        // get the nodes from rp1
        List<Node> rp1 = Helper.asListNode(this.visit(ctx.rp(0)));

        // switch to the original context nodes and get the nodes from rp2
        contextNodes = prevContextNodes;
        List<Node> rp2 = Helper.asListNode(this.visit(ctx.rp(1)));

        Set<Node> joinSet = new HashSet<>();
        joinSet.addAll(rp1);
        joinSet.addAll(rp2);
        contextNodes = new ArrayList<>(joinSet);
        return contextNodes;
    }

    // definition: [[rp[f]]]R(n) = < x | x ← [[rp]]R(n), [[f]]F (x) >
    @Override
    public List<Node> visitRp_filter(XQueryParser.Rp_filterContext ctx) {
        List<Node> rpNodes = Helper.asListNode(this.visit(ctx.rp()));
        List<Node> next = new ArrayList<>();
        for (Node node : rpNodes) {
            List<Node> newContextNodes = new ArrayList<Node>();
            newContextNodes.add(node);
            contextNodes = newContextNodes;
            if (Helper.asBoolean(this.visit(ctx.f()))) {
                next.add(node);
            }
        }
        contextNodes = next;
        return contextNodes;
    }

    @Override
    public Object visitF(XQueryParser.FContext ctx) {
        if (ctx.getChildCount() == 1) {
            return this.visitF_rp(ctx);
        } else if (ctx.getChildCount() == 2) {
            return this.visitF_not(ctx);
        } else if (ctx.getChild(1).getText().equals("=") || ctx.getChild(1).getText().equals("eq")) {
            return this.visitF_eq(ctx);
        } else if (ctx.getChild(1).getText().equals("==") || ctx.getChild(1).getText().equals("is")) {
            return this.visitF_is(ctx);
        } else if (ctx.getChild(1).getText().equals("and")) {
            return this.visitF_and(ctx);
        } else if (ctx.getChild(1).getText().equals("or")) {
            return this.visitF_or(ctx);
        } else if (ctx.getChild(0).getText().equals("(") && ctx.getChild(2).getText().equals(")")) {
            return this.visitF_f(ctx);
        } else
            return null;
    }

    private Object visitF_rp(XQueryParser.FContext ctx) {
        XQueryParser.RpContext rp = ctx.rp(0);
        List<Node> rpNodes =  Helper.asListNode(this.visit(rp));
        return rpNodes.size() != 0;
    }

    private Object visitF_not(XQueryParser.FContext ctx) {
        return !((boolean) this.visit(ctx.getChild(1)));
    }

    private Object visitF_eq(XQueryParser.FContext ctx) {
        List<Node> l1 = (List<Node>) this.visit(ctx.getChild(0));
        List<Node> l2 = (List<Node>) this.visit(ctx.getChild(2));
        return checkEq(l1, l2);
    }

    private Object visitF_is(XQueryParser.FContext ctx) {
        List<Node> l1 = (List<Node>) this.visit(ctx.getChild(0));
        List<Node> l2 = (List<Node>) this.visit(ctx.getChild(2));
        return checkIs(l1, l2);
    }

    private Object visitF_f(XQueryParser.FContext ctx) {
        return this.visit(ctx.getChild(1));
    }

    private Object visitF_and(XQueryParser.FContext ctx) {
        return (boolean) this.visit(ctx.getChild(0)) && (boolean) this.visit(ctx.getChild(2));
    }

    private Object visitF_or(XQueryParser.FContext ctx) {
        return (boolean) this.visit(ctx.getChild(0)) || (boolean) this.visit(ctx.getChild(2));
    }

    @Override
    public Object visitFileName(XQueryParser.FileNameContext ctx) {
        return ctx.getText();
    }

    @Override
    public Object visitTagName(XQueryParser.TagNameContext ctx) {
        return ctx.getText();
    }

    @Override
    public Object visitAttName(XQueryParser.AttNameContext ctx) {
        return ctx.getText();
    }

    @Override

    public Object visit(ParseTree tree) {
        return super.visit(tree);
    }

    @Override
    public Object visitChildren(RuleNode ruleNode) {
        return super.visitChildren(ruleNode);
    }

    @Override
    public Object visitTerminal(TerminalNode terminalNode) {
        return super.visitTerminal(terminalNode);
    }

    @Override
    public Object visitErrorNode(ErrorNode errorNode) {
        return super.visitErrorNode(errorNode);
    }


    private List<Node> getChildren(List<Node> prev) {
        Set<Node> set4next = new HashSet<>();
        for (Node node : prev) {
            // only get the most immediate child
            Node childNode = node.getFirstChild();
            while (childNode != null) {
                set4next.add(childNode);
                childNode = childNode.getNextSibling();
            }
        }
        return new ArrayList<>(set4next);
    }

    private List<Node> getChildrenRecursive(List<Node> prev) {
        Set<Node> set4next = new HashSet<>();
        for (Node node : prev) {
            //First add its own children
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                set4next.add(children.item(i));
            }
            //Then add children's children.
            NodeList nodeList;
            if (node.getNodeType() == Node.DOCUMENT_NODE) {
                nodeList = ((Document) node).getElementsByTagName("*");
            } else{
                nodeList = ((Element) node).getElementsByTagName("*");
            }
            for (int i = 0; i < nodeList.getLength(); i++) {
                NodeList childrenList = nodeList.item(i).getChildNodes();
                for (int j = 0; j < childrenList.getLength(); j++) {
                    set4next.add(childrenList.item(j));
                }
            }
        }
        return new ArrayList<>(set4next);
    }

    private boolean checkEq(List<Node> l1, List<Node> l2) {
        for (Node n1: l1)
            for (Node n2: l2) {
                if (n1.isEqualNode(n2))
                    return true;
            }
        return false;
    }

    private boolean checkIs(List<Node> l1, List<Node> l2) {
        for (Node n1: l1)
            for (Node n2: l2) {
                if (n1.isSameNode(n2))
                    return true;
            }
        return false;
    }

    private void initDoc() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            this.doc = dbf.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException ex) {
            return;
        }

    }
}
