---
layout: default
lang: en
home: /en/
blog: /en/blog/
title: Blog
description: "Practical guides on Nord Pool prices, smart plugs and real savings."
permalink: /en/blog/
---

# Blog

Practical guides on Nord Pool prices, smart plugs and real savings.

{% assign posts = site.posts | where: "lang", "en" %}
{% for post in posts %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%d %B %Y" }}</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
