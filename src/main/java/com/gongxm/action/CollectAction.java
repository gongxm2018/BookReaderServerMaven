package com.gongxm.action;

import java.io.IOException;
import java.util.List;

import org.apache.struts2.ServletActionContext;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;

import com.gongxm.bean.BookInfoAndChapterListRules;
import com.gongxm.bean.BookListRules;
import com.gongxm.domain.request.CollectParam;
import com.gongxm.domain.response.ResponseResult;
import com.gongxm.services.BookChapterService;
import com.gongxm.services.BookListRulesService;
import com.gongxm.services.BookListService;
import com.gongxm.utils.CollectUtils;
import com.gongxm.utils.GsonUtils;

@Controller
@Scope("prototype")
@Namespace("/action")
@ParentPackage("struts-default")
public class CollectAction extends BaseAction {

	private static final long serialVersionUID = 1L;
	@Autowired
	BookListRulesService rulesService;
	@Autowired
	BookListService service;
	@Autowired
	BookChapterService chapterService;

	// 显示所有的规则
	@Action(value = "collect", results = { @Result(name = "success", location = "/WEB-INF/collectManagement.jsp") })
	public String showAllRules() {
		List<BookListRules> rulesList = rulesService.findAll();
		ServletActionContext.getRequest().getSession().setAttribute("rulesList", rulesList);
		return SUCCESS;
	}


	// 采集书籍列表
	@Action("collectBookList")
	public void collectBookList() {
		ResponseResult result = new ResponseResult();
		CollectParam param = GsonUtils.fromJson(getData(), CollectParam.class);
		if (param != null) {
			int id = param.getId();
			if (id > 0) {
				BookListRules bookListRules = rulesService.findById(id);
				new Thread() {
					@Transactional
					public void run() {
						CollectUtils.collectBookList(bookListRules);
					};
				}.start();
				result.setSuccess();
			}
		}
		String json = GsonUtils.toJson(result);
		write(json);
	}

	// 采集书籍信息
	@Action("collectBookInfo")
	public void collectBookInfo() {
		WebApplicationContext context = ContextLoader.getCurrentWebApplicationContext();
		ResponseResult result = new ResponseResult();
		CollectParam param = GsonUtils.fromJson(getData(), CollectParam.class);
		if (param != null) {
			int id = param.getId();
			if (id > 0) {
				BookListRules bookListRules = rulesService.findById(id);
				BookInfoAndChapterListRules rules = bookListRules.getRules();
				Hibernate.initialize(rules);// 把内容加载出来, 不可删除
				boolean update = param.isUpdate();
				new Thread() {
					@Transactional
					public void run() {
						try {
							CollectUtils.collectBookInfo(context,bookListRules, update);
						} catch (IOException e) {
							e.printStackTrace();
						}
					};
				}.start();
				result.setSuccess();
			}
		}
		String json = GsonUtils.toJson(result);
		write(json);
	}


}
