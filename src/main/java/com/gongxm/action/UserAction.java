package com.gongxm.action;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.gongxm.bean.User;
import com.gongxm.domain.OpenIdResult;
import com.gongxm.domain.request.LoginParam;
import com.gongxm.domain.request.ThirdSessionParam;
import com.gongxm.domain.request.UserInfoParam;
import com.gongxm.domain.response.LoginResult;
import com.gongxm.domain.response.ResponseResult;
import com.gongxm.domain.response.UserInfo;
import com.gongxm.domain.response.UserResult;
import com.gongxm.services.UserService;
import com.gongxm.utils.GsonUtils;
import com.gongxm.utils.HttpUtils;
import com.gongxm.utils.MyConstants;
import com.gongxm.utils.StringConstants;
import com.gongxm.utils.TextUtils;
import com.gongxm.utils.TimeUtils;
import com.gongxm.utils.WxAuthUtil;

@Controller
@Scope("prototype")
@Namespace("/action")
@ParentPackage("struts-default")
public class UserAction extends BaseAction {
	private static final long serialVersionUID = 1L;

	@Autowired
	UserService userService;

	@Action("wxlogin")
	public void wxlogin() {
		String text = getData();
		LoginResult loginResult = new LoginResult();
		try {
			LoginParam loginParam = GsonUtils.fromJson(text, LoginParam.class);

			String code = loginParam.getJs_code();

			if (TextUtils.notEmpty(code)) {
				String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + MyConstants.APPID + "&secret="
						+ MyConstants.APP_SECRET + "&js_code=" + code + "&grant_type=" + MyConstants.GRANT_TYPE;
				try {
					String data = HttpUtils.executGet(url);

					OpenIdResult result = GsonUtils.fromJson(data, OpenIdResult.class);

					int errcode = result.getErrcode();

					// 如果错误码为0, 创建一个用户
					if (errcode == 0) {
						String openid = result.getOpenid();
						// 先查询用户是否存在
						User user = userService.findUserByOpenId(openid);

						String session_key = result.getSession_key();
						if (user == null) {
							String thirdSession = WxAuthUtil.create3rdSession();
							user = new User();
							user.setOpenid(openid);
							user.setSession_key(session_key);
							user.setPermissions(MyConstants.ROLE_TEST);
							user.setRegistTime(TimeUtils.getCurrentTime());
							user.setThirdSession(thirdSession);

							userService.addUser(user);

							result = new OpenIdResult();
							// 3rdSessionId返回给客户端
							loginResult.setThirdSession(thirdSession);
							loginResult.setErrcode(MyConstants.SUCCESS);
						} else {
							String thirdSession = user.getThirdSession();
							loginResult.setThirdSession(thirdSession);
							loginResult.setErrcode(MyConstants.SUCCESS);

							user.setOpenid(openid);
							user.setSession_key(session_key);
							userService.update(user);
						}
					} else {
						loginResult.setErrmsg(StringConstants.WX_HTTP_REQUEST_ERROR);
					}

				} catch (Exception e) {
					e.printStackTrace();
					loginResult.setErrmsg(e.getMessage());
				}
			}

		} catch (Exception e1) {
			e1.printStackTrace();
			loginResult.setErrmsg(StringConstants.JSON_PARSE_ERROR);
		}
		String json = GsonUtils.toJson(loginResult);
		write(json);

	}

	@Action("saveUserInfo")
	public void saveUserInfo() {
		ResponseResult result = new ResponseResult(MyConstants.FAILURE, "用户信息存储失败!");
		try {
			UserInfoParam userInfoParam = GsonUtils.fromJson(getData(), UserInfoParam.class);
			String thirdSession = userInfoParam.getThirdSession();
			if (TextUtils.notEmpty(thirdSession)) {
				try {
					User user = userService.findUserByThirdSession(thirdSession);
					if (user != null) {
						String encryptedData = userInfoParam.getEncryptedData();
						String sessionKey = user.getSession_key();
						String iv = userInfoParam.getIv();
						String userInfo = WxAuthUtil.decodeUserInfo(encryptedData, iv, sessionKey);
						if (userInfo != null) {
							UserResult resultUser = GsonUtils.fromJson(userInfo, UserResult.class);
							user.setAvatarUrl(resultUser.getAvatarUrl());
							user.setCity(resultUser.getCity());
							user.setCountry(resultUser.getCountry());
							user.setGender(resultUser.getGender());
							user.setNickName(resultUser.getNickName());
							user.setUsername(resultUser.getNickName());
							user.setProvince(resultUser.getProvince());
							userService.update(user);
							result.setErrcode(MyConstants.SUCCESS);
							result.setErrmsg("用户信息存储成功!");
						}
					}
				} catch (Exception e) {
					result.setErrmsg(e.getMessage());
				}
			}
		} catch (Exception e1) {
			e1.printStackTrace();
			result.setErrmsg(StringConstants.JSON_PARSE_ERROR);
		}
		String json = GsonUtils.toJson(result);
		write(json);
	}

	@Action("getUserInfo")
	public void getUserInfo() {
		UserInfo info = new UserInfo();
		try {
			ThirdSessionParam param = GsonUtils.fromJson(getData(), ThirdSessionParam.class);
			if (param != null) {
				String thirdSession = param.getThirdSession();
				if (TextUtils.notEmpty(thirdSession)) {
					User user = userService.findUserByThirdSession(thirdSession);
					if (user != null) {
						info.setUser(user);
						info.setErrcode(MyConstants.SUCCESS);
						info.setErrmsg("获取用户信息成功!");
					} else {
						info.setErrmsg("用户不存在!");
					}
				} else {
					info.setErrmsg("thirdSession为空");
				}
			} else {
				info.setErrmsg("参数为空");
			}
		} catch (Exception e) {
			e.printStackTrace();
			info.setErrmsg(StringConstants.JSON_PARSE_ERROR);
		}
		String json = GsonUtils.toJson(info);

		write(json);
	}

	// 验证用户名
	@Action("validateUserName")
	public void validateUserName() {
		HttpServletRequest request = ServletActionContext.getRequest();
		String username = request.getParameter("username");
		User user = userService.findUserByName(username);
		ResponseResult result = new ResponseResult(MyConstants.FAILURE, "请求失败");

		if (user != null) {
			result.setErrmsg("该用户名已被占用!");
		} else {
			result.setErrcode(MyConstants.SUCCESS);
			result.setErrmsg("恭喜您,该用户名可以使用!");
		}
		String json = GsonUtils.toJson(result);
		write(json);
	}

	// 退出账号
	@Action("logout")
	public void logout() throws IOException {
		HttpServletRequest request = ServletActionContext.getRequest();
		request.getSession().removeAttribute("user");
		Cookie cookie = new Cookie("user", "");
		cookie.setPath(request.getContextPath());
		cookie.setMaxAge(0);

		HttpServletResponse response = ServletActionContext.getResponse();
		response.addCookie(cookie);
		response.setCharacterEncoding(MyConstants.DEFAULT_ENCODING);
		response.setContentType("text/html;charset=" + MyConstants.DEFAULT_ENCODING);
		response.getWriter().write("<h1 align='center'><font color='green' size=5>注销成功！2秒后转到主页！</font></h1>");
		response.setHeader("refresh", "2;url=" + request.getContextPath());
	}

	// 获取所有用户
	@Action("getAllUser")
	public void getAllUser() {
		ThirdSessionParam param = GsonUtils.fromJson(getData(), ThirdSessionParam.class);
		ResponseResult result = new ResponseResult();
		if (param != null) {
			String thirdSession = param.getThirdSession();
			if (TextUtils.notEmpty(thirdSession)) {
				User user = userService.findUserByThirdSession(thirdSession);
				if (user != null) {
					String permissions = user.getPermissions();
					if (MyConstants.ROLE_ROOT.equals(permissions)) {
						List<User> list = userService.findAll();
						result.setResult(list);
						result.setSuccess();
					}
				}
			}
		}
		String json = GsonUtils.toJson(result);
		write(json);
	}

	
	//切换用户权限
	@Action("changePermissions")
	public void changePermissions() {
		ThirdSessionParam param = GsonUtils.fromJson(getData(), ThirdSessionParam.class);
		ResponseResult result = new ResponseResult();
		if (param != null) {
			int id = param.getId();
			if(id>0) {
				String thirdSession = param.getThirdSession();
				if(TextUtils.notEmpty(thirdSession)) {
					User manager = userService.findUserByThirdSession(thirdSession);
					if (manager != null) {
						String permissions = manager.getPermissions();
						if (MyConstants.ROLE_ROOT.equals(permissions)) {
							User user = userService.findById(id);
							if(user!=null) {
								String perm = user.getPermissions();
								if(MyConstants.ROLE_USER.equals(perm)) {
									user.setPermissions(MyConstants.ROLE_TEST);
								}else {
									user.setPermissions(MyConstants.ROLE_USER);
								}
								userService.update(user);
								result.setSuccess();
							}
						}
					}	
				}
			}
		}
		
		String json = GsonUtils.toJson(result);
		write(json);

	}

}
