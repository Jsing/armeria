(window.webpackJsonp=window.webpackJsonp||[]).push([[77],{"1lec":function(e){e.exports=JSON.parse('{"/release-notes/1.13.4":"v1.13.4","/release-notes/1.13.3":"v1.13.3","/release-notes/1.13.2":"v1.13.2","/release-notes/1.13.1":"v1.13.1","/release-notes/1.13.0":"v1.13.0","/release-notes/1.12.0":"v1.12.0","/release-notes/1.11.0":"v1.11.0","/release-notes/1.10.0":"v1.10.0","/release-notes/1.9.2":"v1.9.2","/release-notes/1.9.1":"v1.9.1","/release-notes/1.9.0":"v1.9.0","/release-notes/1.8.0":"v1.8.0","/release-notes/1.7.2":"v1.7.2","/release-notes/1.7.1":"v1.7.1","/release-notes/1.7.0":"v1.7.0","/release-notes/1.6.0":"v1.6.0"}')},"2+3N":function(e){e.exports=JSON.parse('{"/news/20211029-newsletter-3":"Armeria Newsletter vol. 3","/news/20210202-newsletter-2":"Armeria Newsletter vol. 2","/news/20200703-newsletter-1":"Armeria Newsletter vol. 1","/news/20200514-newsletter-0":"Armeria Newsletter vol. 0"}')},JkCF:function(e,t,s){"use strict";s("tU7J");var a=s("wFql"),n=s("q1tI"),r=s.n(n),l=s("2+3N"),i=s("1lec"),o=s("+ejy"),c=s("+9zj"),b=a.a.Title;t.a=function(e){var t={},s={},a={root:{"Latest news items":"/news","Latest release notes":"/release-notes","Past news items":"/news/list","Past release notes":"/release-notes/list"},"Recent news items":t,"Recent releases":s};Object.entries(l).forEach((function(e){var s=e[0],a=e[1];t[a]=s})),Object.entries(i).forEach((function(e){var t=e[0],a=e[1];s[a]=t}));var n=Object(c.a)(e.location),h=e.version||n.substring(n.lastIndexOf("/")+1);return h.match(/^[0-9]/)||(h=void 0),r.a.createElement(o.a,Object.assign({},e,{candidateMdxNodes:[],index:a,prefix:"release-notes",pageTitle:h?h+" release notes":e.pageTitle,pageTitleSuffix:"Armeria release notes"}),h?r.a.createElement(b,{id:"release-notes",level:1},r.a.createElement("a",{href:"#release-notes","aria-label":"release notes permalink",className:"anchor before"},r.a.createElement("svg",{"aria-hidden":"true",focusable:"false",height:"16",version:"1.1",viewBox:"0 0 16 16",width:"16"},r.a.createElement("path",{fillRule:"evenodd",d:"M4 9h1v1H4c-1.5 0-3-1.69-3-3.5S2.55 3 4 3h4c1.45 0 3 1.69 3 3.5 0 1.41-.91 2.72-2 3.25V8.59c.58-.45 1-1.27 1-2.09C10 5.22 8.98 4 8 4H4c-.98 0-2 1.22-2 2.5S3 9 4 9zm9-3h-1v1h1c1 0 2 1.22 2 2.5S13.98 12 13 12H9c-.98 0-2-1.22-2-2.5 0-.83.42-1.64 1-2.09V6.25c-1.09.53-2 1.84-2 3.25C6 11.31 7.55 13 9 13h4c1.45 0 3-1.69 3-3.5S14.5 6 13 6z"}))),h," release notes"):"",e.children)}},"npN/":function(e,t,s){"use strict";s.r(t),s.d(t,"_frontmatter",(function(){return l})),s.d(t,"default",(function(){return c}));var a=s("zLVn"),n=(s("q1tI"),s("7ljp")),r=s("JkCF"),l={},i={_frontmatter:l},o=r.a;function c(e){var t=e.components,s=Object(a.a)(e,["components"]);return Object(n.b)(o,Object.assign({},i,s,{components:t,mdxType:"MDXLayout"}),Object(n.b)("p",{className:"date"},"14th August 2019"),Object(n.b)("h2",{id:"security",style:{position:"relative"}},Object(n.b)("a",{parentName:"h2",href:"#security","aria-label":"security permalink",className:"anchor before"},Object(n.b)("svg",{parentName:"a","aria-hidden":"true",focusable:"false",height:"16",version:"1.1",viewBox:"0 0 16 16",width:"16"},Object(n.b)("path",{parentName:"svg",fillRule:"evenodd",d:"M4 9h1v1H4c-1.5 0-3-1.69-3-3.5S2.55 3 4 3h4c1.45 0 3 1.69 3 3.5 0 1.41-.91 2.72-2 3.25V8.59c.58-.45 1-1.27 1-2.09C10 5.22 8.98 4 8 4H4c-.98 0-2 1.22-2 2.5S3 9 4 9zm9-3h-1v1h1c1 0 2 1.22 2 2.5S13.98 12 13 12H9c-.98 0-2-1.22-2-2.5 0-.83.42-1.64 1-2.09V6.25c-1.09.53-2 1.84-2 3.25C6 11.31 7.55 13 9 13h4c1.45 0 3-1.69 3-3.5S14.5 6 13 6z"}))),"Security"),Object(n.b)("p",null,"This release updates Netty from 4.1.38 to 4.1.39 to address the HTTP/2 security issues found in its previous versions. Please upgrade as soon as possible if your application serves the traffic from untrusted environment such as the Internet. See ",Object(n.b)("a",{parentName:"p",href:"https://netty.io/news/2019/08/13/4-1-39-Final.html"},"Netty 4.1.39 release news")," and ",Object(n.b)("a",{parentName:"p",href:"https://github.com/Netflix/security-bulletins/blob/master/advisories/third-party/2019-002.md"},"Netflix security bulletins")," for more information."),Object(n.b)("h2",{id:"dependencies",style:{position:"relative"}},Object(n.b)("a",{parentName:"h2",href:"#dependencies","aria-label":"dependencies permalink",className:"anchor before"},Object(n.b)("svg",{parentName:"a","aria-hidden":"true",focusable:"false",height:"16",version:"1.1",viewBox:"0 0 16 16",width:"16"},Object(n.b)("path",{parentName:"svg",fillRule:"evenodd",d:"M4 9h1v1H4c-1.5 0-3-1.69-3-3.5S2.55 3 4 3h4c1.45 0 3 1.69 3 3.5 0 1.41-.91 2.72-2 3.25V8.59c.58-.45 1-1.27 1-2.09C10 5.22 8.98 4 8 4H4c-.98 0-2 1.22-2 2.5S3 9 4 9zm9-3h-1v1h1c1 0 2 1.22 2 2.5S13.98 12 13 12H9c-.98 0-2-1.22-2-2.5 0-.83.42-1.64 1-2.09V6.25c-1.09.53-2 1.84-2 3.25C6 11.31 7.55 13 9 13h4c1.45 0 3-1.69 3-3.5S14.5 6 13 6z"}))),"Dependencies"),Object(n.b)("ul",null,Object(n.b)("li",{parentName:"ul"},"Brave 5.6.9 -> 5.6.10"),Object(n.b)("li",{parentName:"ul"},"gRPC 1.22.1 -> 1.22.2"),Object(n.b)("li",{parentName:"ul"},"Netty 4.1.38 -> 4.1.39"),Object(n.b)("li",{parentName:"ul"},"SLF4J 1.7.27 -> 1.7.28")))}c.isMDXComponent=!0}}]);
//# sourceMappingURL=component---src-pages-release-notes-0-90-2-mdx-467c298836a09e6e73c1.js.map